package easyagenda.horarios;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import org.bson.Document;
import org.bson.types.ObjectId;

import com.mongodb.client.model.Filters;
import com.sun.net.httpserver.HttpExchange;

import easyagenda.Database;
import easyagenda.Json;
import easyagenda.Server;
import easyagenda.auth.Auth;
import easyagenda.auth.Usuarios;

/**
 * Rotas dos horários livres do professor (tarefa 2.2).
 */
public final class HorarioRoutes {

    private HorarioRoutes() {
    }

    /**
     * GET /horarios?de=2026-10-19&ate=2026-10-25  (só professor)
     * Devolve os horários do professor logado, dizendo quais já têm aluno.
     */
    public static void listarMeus(HttpExchange exchange) throws IOException {
        if (idNoCaminho(exchange, "/horarios") != null) {
            Json.erro(exchange, 404, "rota não encontrada");
            return;
        }
        Auth.Usuario professor = Server.usuarioLogado(exchange);
        String de = query(exchange, "de");
        String ate = query(exchange, "ate");
        if (!diaValido(de) || !diaValido(ate)) {
            Json.erro(exchange, 400, "informe de e ate no formato 2026-10-20");
            return;
        }

        List<Document> resposta = new ArrayList<>();
        for (Document h : Horarios.listarDoProfessor(new ObjectId(professor.id()), de, ate)) {
            Document json = Horarios.paraJson(h);
            Document aula = aulaDoHorario(h);
            json.append("reservado", aula != null);
            json.append("aluno", aula == null ? null : nomeDoUsuario(aula.getObjectId("alunoId")));
            resposta.add(json);
        }
        Json.enviar(exchange, 200, new Document("horarios", resposta));
    }

    /**
     * POST /horarios  (só professor)
     * Recebe: {"dia", "horaInicio", "horaFim", "modalidade"}
     * 201 com o horário criado. 400 dado inválido, 409 se sobrepõe outro horário.
     */
    public static void criar(HttpExchange exchange) throws IOException {
        if (idNoCaminho(exchange, "/horarios") != null) {
            Json.erro(exchange, 404, "rota não encontrada");
            return;
        }
        Document corpo = Json.lerCorpo(exchange);
        if (corpo == null) {
            Json.erro(exchange, 400, "envie um JSON com dia, horaInicio, horaFim e modalidade");
            return;
        }
        String dia = Json.texto(corpo, "dia");
        String inicio = Json.texto(corpo, "horaInicio");
        String fim = Json.texto(corpo, "horaFim");
        String modalidade = Json.texto(corpo, "modalidade");

        if (!diaValido(dia) || !horaValida(inicio) || !horaValida(fim)) {
            Json.erro(exchange, 400, "use dia no formato 2026-10-20 e horas no formato 14:00");
            return;
        }
        if (fim.compareTo(inicio) <= 0) {
            Json.erro(exchange, 400, "o fim precisa ser depois do início");
            return;
        }
        if (!modalidadeValida(modalidade)) {
            Json.erro(exchange, 400, "modalidade deve ser 'online' ou 'presencial'");
            return;
        }

        ObjectId professorId = new ObjectId(Server.usuarioLogado(exchange).id());
        Document novo = new Document().append("dia", dia).append("horaInicio", inicio);
        if (Horarios.jaPassou(novo)) {
            Json.erro(exchange, 400, "não dá para abrir um horário que já passou");
            return;
        }
        if (Horarios.temSobreposicao(professorId, dia, inicio, fim)) {
            Json.erro(exchange, 409, "você já tem um horário nesse intervalo");
            return;
        }

        Document criado = Horarios.criar(professorId, dia, inicio, fim, modalidade);
        Json.enviar(exchange, 201, Horarios.paraJson(criado).append("reservado", false));
    }

    /**
     * PUT /horarios/{id}  (só o professor dono)
     * Recebe: {"modalidade"}. 409 se já tem aluno nesse horário.
     */
    public static void mudarModalidade(HttpExchange exchange) throws IOException {
        Document horario = horarioDoDono(exchange);
        if (horario == null) {
            return; // horarioDoDono já respondeu o erro
        }
        Document corpo = Json.lerCorpo(exchange);
        String modalidade = corpo == null ? null : Json.texto(corpo, "modalidade");
        if (!modalidadeValida(modalidade)) {
            Json.erro(exchange, 400, "modalidade deve ser 'online' ou 'presencial'");
            return;
        }
        if (Horarios.estaReservado(horario)) {
            Json.erro(exchange, 409, "esse horário já tem aluno e não pode mudar");
            return;
        }
        Horarios.mudarModalidade(horario.getObjectId("_id"), modalidade);
        horario.put("modalidade", modalidade);
        Json.enviar(exchange, 200, Horarios.paraJson(horario).append("reservado", false));
    }

    /**
     * DELETE /horarios/{id}  (só o professor dono)
     * 204 se removeu. 409 se já tem aluno nesse horário.
     */
    public static void remover(HttpExchange exchange) throws IOException {
        Document horario = horarioDoDono(exchange);
        if (horario == null) {
            return;
        }
        if (Horarios.estaReservado(horario)) {
            Json.erro(exchange, 409, "esse horário já tem aluno e não pode ser removido");
            return;
        }
        Horarios.remover(horario.getObjectId("_id"));
        exchange.sendResponseHeaders(204, -1);
    }

    /**
     * GET /professores/{id}/horarios?de=&ate=  (rota aberta: é o link do professor)
     * Devolve o nome do professor e só os horários livres que ainda não passaram.
     */
    public static void listarDoProfessor(HttpExchange exchange) throws IOException {
        // caminho esperado: /professores/{id}/horarios
        String[] partes = exchange.getRequestURI().getPath().split("/");
        if (partes.length != 4 || !"horarios".equals(partes[3]) || !ObjectId.isValid(partes[2])) {
            Json.erro(exchange, 404, "professor não encontrado");
            return;
        }
        ObjectId professorId = new ObjectId(partes[2]);
        Document professor = Usuarios.colecao().find(Filters.and(
                Filters.eq("_id", professorId),
                Filters.eq("tipo", Usuarios.PROFESSOR))).first();
        if (professor == null) {
            Json.erro(exchange, 404, "professor não encontrado");
            return;
        }

        String de = query(exchange, "de");
        String ate = query(exchange, "ate");
        if (de == null) {
            de = LocalDate.now(Horarios.FUSO).toString();
        }
        if (ate == null) {
            ate = LocalDate.parse(de).plusDays(30).toString();
        }
        if (!diaValido(de) || !diaValido(ate)) {
            Json.erro(exchange, 400, "informe de e ate no formato 2026-10-20");
            return;
        }

        List<Document> livres = new ArrayList<>();
        for (Document h : Horarios.listarDoProfessor(professorId, de, ate)) {
            if (!Horarios.jaPassou(h) && !Horarios.estaReservado(h)) {
                livres.add(Horarios.paraJson(h));
            }
        }
        Json.enviar(exchange, 200, new Document()
                .append("professor", new Document("id", professorId.toHexString()).append("nome", professor.getString("nome")))
                .append("horarios", livres));
    }

    // ----- ajudantes -----

    /** Busca o horário de /horarios/{id} e confere se é do professor logado. Responde o erro se não for. */
    private static Document horarioDoDono(HttpExchange exchange) throws IOException {
        String id = idNoCaminho(exchange, "/horarios");
        if (id == null || !ObjectId.isValid(id)) {
            Json.erro(exchange, 404, "horário não encontrado");
            return null;
        }
        Document horario = Horarios.buscarPorId(new ObjectId(id));
        if (horario == null) {
            Json.erro(exchange, 404, "horário não encontrado");
            return null;
        }
        String professorId = Server.usuarioLogado(exchange).id();
        if (!horario.getObjectId("professorId").toHexString().equals(professorId)) {
            Json.erro(exchange, 403, "esse horário é de outro professor");
            return null;
        }
        return horario;
    }

    /** Aula ativa (agendada ou realizada) nesse horário, ou null. */
    private static Document aulaDoHorario(Document horario) {
        return Database.get().getCollection("aulas").find(Filters.and(
                Filters.eq("professorId", horario.getObjectId("professorId")),
                Filters.eq("dataHora", Horarios.inicio(horario)),
                Filters.in("status", "agendada", "realizada"))).first();
    }

    private static String nomeDoUsuario(ObjectId id) {
        Document usuario = Usuarios.colecao().find(Filters.eq("_id", id)).first();
        return usuario == null ? "Aluno" : usuario.getString("nome");
    }

    /** Em /horarios/abc devolve "abc"; em /horarios devolve null. */
    private static String idNoCaminho(HttpExchange exchange, String prefixo) {
        String resto = exchange.getRequestURI().getPath().substring(prefixo.length());
        if (resto.startsWith("/")) {
            resto = resto.substring(1);
        }
        return resto.isEmpty() ? null : resto;
    }

    /** Valor de um parâmetro da URL (?nome=valor), ou null. */
    private static String query(HttpExchange exchange, String nome) {
        String qs = exchange.getRequestURI().getRawQuery();
        if (qs == null) {
            return null;
        }
        for (String par : qs.split("&")) {
            String[] chaveValor = par.split("=", 2);
            if (chaveValor[0].equals(nome) && chaveValor.length == 2) {
                return URLDecoder.decode(chaveValor[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static boolean diaValido(String dia) {
        try {
            return dia != null && LocalDate.parse(dia).toString().equals(dia);
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private static boolean horaValida(String hora) {
        try {
            return hora != null && hora.length() == 5 && LocalTime.parse(hora).toString().equals(hora);
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private static boolean modalidadeValida(String modalidade) {
        return Horarios.ONLINE.equals(modalidade) || Horarios.PRESENCIAL.equals(modalidade);
    }
}
