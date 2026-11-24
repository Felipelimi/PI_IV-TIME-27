package easyagenda.aulas;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.bson.Document;
import org.bson.types.ObjectId;

import com.mongodb.client.model.Filters;
import com.sun.net.httpserver.HttpExchange;

import easyagenda.Json;
import easyagenda.Server;
import easyagenda.auth.Auth;
import easyagenda.auth.Usuarios;
import easyagenda.sockets.ClienteDeSockets;
import easyagenda.sockets.Comunicado;
import easyagenda.sockets.PedidoDeAgendamento;
import easyagenda.sockets.PedidoDeCancelamento;
import easyagenda.sockets.PedidoDeRemarcacao;
import easyagenda.sockets.Resultado;

/**
 * Rotas das aulas: agendamento (tarefa 3.1), cancelar e remarcar (tarefa 4.1),
 * próximas aulas e histórico (tarefa 4.3).
 *
 * Agendar, cancelar e remarcar são feitos pelo servidor de sockets
 * (easyagenda.sockets.Servidor): aqui a API só confere o login, monta o
 * pedido e devolve ao site o Resultado que voltou. As regras estão em
 * easyagenda.sockets.OperacoesDeAula.
 */
public final class AulaRoutes {

    /** Maior prazo que o professor pode pedir: 30 dias. */
    private static final int PRAZO_MAXIMO_HORAS = 720;

    private AulaRoutes() {
    }

    /**
     * POST em /aulas e nos caminhos abaixo dele (aluno agenda; aluno e professor alteram suas aulas):
     * /aulas, /aulas/{id}/cancelar e /aulas/{id}/remarcar.
     */
    public static void postar(HttpExchange exchange) throws IOException {
        String caminho = exchange.getRequestURI().getPath();
        if ("/aulas".equals(caminho)) {
            if (!Usuarios.ALUNO.equals(Server.usuarioLogado(exchange).tipo())) {
                Json.erro(exchange, 403, "acesso permitido apenas para aluno");
                return;
            }
            agendar(exchange);
            return;
        }
        // caminho esperado: /aulas/{id}/cancelar ou /aulas/{id}/remarcar
        String[] partes = caminho.split("/");
        if (partes.length == 4 && "cancelar".equals(partes[3])) {
            cancelar(exchange, partes[2]);
        } else if (partes.length == 4 && "remarcar".equals(partes[3])) {
            remarcar(exchange, partes[2]);
        } else {
            Json.erro(exchange, 404, "rota não encontrada");
        }
    }

    /**
     * POST /aulas  (só aluno)
     * Recebe: {"horarioId"} — um dos horários de GET /professores/{id}/horarios.
     * 201 com a aula marcada. 400 dado inválido ou horário que já passou,
     * 404 horário inexistente, 409 "horário indisponível" se alguém marcou antes.
     */
    private static void agendar(HttpExchange exchange) throws IOException {
        String alunoId = Server.usuarioLogado(exchange).id();
        repassar(exchange, new PedidoDeAgendamento(horarioIdDoCorpo(exchange), alunoId));
    }

    /**
     * POST /aulas/{id}/cancelar  (aluno ou professor da aula)
     * 200 com a aula cancelada. O horário volta a ficar livre para outros alunos.
     * 404 aula inexistente ou de outro usuário, 409 se a aula não está mais
     * agendada, já começou ou se falta menos tempo que o prazo do professor.
     */
    private static void cancelar(HttpExchange exchange, String aulaId) throws IOException {
        Auth.Usuario usuario = Server.usuarioLogado(exchange);
        repassar(exchange, new PedidoDeCancelamento(aulaId, usuario.id(), usuario.tipo()));
    }

    /**
     * POST /aulas/{id}/remarcar  (aluno ou professor da aula)
     * Recebe: {"horarioId"} — outro horário livre do mesmo professor.
     * Agenda a aula nova (mesmas regras da 3.1) e cancela a antiga.
     * 201 com a aula nova e "remarcadaDe" (o id da aula antiga).
     * Se o horário novo estiver ocupado (409), a aula antiga continua marcada.
     */
    private static void remarcar(HttpExchange exchange, String aulaId) throws IOException {
        Auth.Usuario usuario = Server.usuarioLogado(exchange);
        repassar(exchange, new PedidoDeRemarcacao(aulaId, horarioIdDoCorpo(exchange), usuario.id(), usuario.tipo()));
    }

    /**
     * GET /professor/prazo-cancelamento  (só professor)
     * Devolve {"prazoCancelamentoHoras"}: com quantas horas de antecedência,
     * no mínimo, o aluno pode cancelar ou remarcar. Padrão: 24.
     */
    public static void verPrazo(HttpExchange exchange) throws IOException {
        if (!"/professor/prazo-cancelamento".equals(exchange.getRequestURI().getPath())) {
            Json.erro(exchange, 404, "rota não encontrada");
            return;
        }
        ObjectId professorId = new ObjectId(Server.usuarioLogado(exchange).id());
        Json.enviar(exchange, 200, new Document("prazoCancelamentoHoras", Usuarios.prazoCancelamentoHoras(professorId)));
    }

    /**
     * PUT /professor/prazo-cancelamento  (só professor)
     * Recebe: {"prazoCancelamentoHoras": 24} — número inteiro de 0 a 720 (30 dias).
     * 0 quer dizer que o aluno pode cancelar até a aula começar.
     */
    public static void mudarPrazo(HttpExchange exchange) throws IOException {
        if (!"/professor/prazo-cancelamento".equals(exchange.getRequestURI().getPath())) {
            Json.erro(exchange, 404, "rota não encontrada");
            return;
        }
        Document corpo = Json.lerCorpo(exchange);
        Object valor = corpo == null ? null : corpo.get("prazoCancelamentoHoras");
        if (!(valor instanceof Number numero) || numero.doubleValue() != Math.floor(numero.doubleValue())
                || numero.doubleValue() < 0 || numero.doubleValue() > PRAZO_MAXIMO_HORAS) {
            Json.erro(exchange, 400, "envie prazoCancelamentoHoras como um número inteiro de 0 a " + PRAZO_MAXIMO_HORAS);
            return;
        }
        int horas = numero.intValue();
        Usuarios.definirPrazoCancelamento(new ObjectId(Server.usuarioLogado(exchange).id()), horas);
        Json.enviar(exchange, 200, new Document("prazoCancelamentoHoras", horas));
    }

    /**
     * GET /aulas/proximas  (qualquer usuário logado)
     * Aulas agendadas que ainda não começaram, da mais próxima para a mais distante.
     */
    public static void listarProximas(HttpExchange exchange) throws IOException {
        if (!"/aulas/proximas".equals(exchange.getRequestURI().getPath())) {
            Json.erro(exchange, 404, "rota não encontrada");
            return;
        }
        Json.enviar(exchange, 200, new Document("aulas", paraJson(Aulas.proximas(Server.usuarioLogado(exchange)))));
    }

    /**
     * GET /aulas/historico  (qualquer usuário logado)
     * Aulas que já passaram e aulas canceladas, da mais recente para a mais antiga.
     */
    public static void listarHistorico(HttpExchange exchange) throws IOException {
        if (!"/aulas/historico".equals(exchange.getRequestURI().getPath())) {
            Json.erro(exchange, 404, "rota não encontrada");
            return;
        }
        Json.enviar(exchange, 200, new Document("aulas", paraJson(Aulas.historico(Server.usuarioLogado(exchange)))));
    }

    // ----- ajudantes -----

    /** O {"horarioId"} do corpo, ou null se não veio (o servidor de sockets responde o erro). */
    private static String horarioIdDoCorpo(HttpExchange exchange) throws IOException {
        Document corpo = Json.lerCorpo(exchange);
        return corpo == null ? null : Json.texto(corpo, "horarioId");
    }

    /**
     * Manda o pedido ao servidor de sockets e devolve ao site o Resultado
     * (mesmo código e mesmo JSON). 503 se o servidor de sockets não responder.
     */
    private static void repassar(HttpExchange exchange, Comunicado pedido) throws IOException {
        Resultado resultado;
        try {
            resultado = ClienteDeSockets.pedir(pedido);
        } catch (Exception erro) {
            System.err.println("[sockets] sem resposta de " + ClienteDeSockets.endereco()
                    + " para \"" + pedido + "\": " + erro.getMessage());
            Json.erro(exchange, 503, "o agendamento está indisponível agora; tente de novo em instantes");
            return;
        }
        Json.enviarTexto(exchange, resultado.getCodigo(), resultado.getJson());
    }

    /** Aulas no formato do site, com o nome do professor e do aluno para a tela mostrar. */
    private static List<Document> paraJson(List<Document> aulas) {
        List<Document> resposta = new ArrayList<>();
        for (Document aula : aulas) {
            resposta.add(Aulas.paraJson(aula, Aulas.horarioDaAula(aula))
                    .append("prazoCancelamentoHoras", Usuarios.prazoCancelamentoHoras(aula.getObjectId("professorId")))
                    .append("professorNome", nomeDoUsuario(aula.getObjectId("professorId")))
                    .append("alunoNome", nomeDoUsuario(aula.getObjectId("alunoId"))));
        }
        return resposta;
    }

    private static String nomeDoUsuario(ObjectId id) {
        Document usuario = Usuarios.colecao().find(Filters.eq("_id", id)).first();
        return usuario == null ? null : usuario.getString("nome");
    }
}
