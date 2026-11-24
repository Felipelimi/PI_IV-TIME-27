package easyagenda.seed;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.bson.Document;
import org.bson.types.ObjectId;

import com.mongodb.client.model.Filters;

import easyagenda.Database;
import easyagenda.aulas.Aulas;
import easyagenda.auth.Usuarios;
import easyagenda.horarios.Horarios;
import easyagenda.sockets.OperacoesDeAula;
import easyagenda.sockets.PedidoDeAgendamento;
import easyagenda.sockets.PedidoDeCancelamento;
import easyagenda.sockets.PedidoDeRemarcacao;
import easyagenda.sockets.Resultado;

/**
 * Popula o banco com dados de demonstração: 2 professores, 3 alunos, horários
 * livres, aulas futuras (agendadas, remarcadas e canceladas) e aulas passadas
 * para o histórico.
 *
 * Usa o banco da MONGODB_URI (local ou Atlas). Não precisa da API rodando:
 * agendar, cancelar e remarcar passam pelas mesmas regras do servidor de
 * sockets (OperacoesDeAula). Só as aulas passadas são gravadas direto no banco,
 * porque as regras não deixam marcar aula no passado.
 *
 *   mvn compile exec:java -Dexec.mainClass=easyagenda.seed.Seed               # popula
 *   mvn compile exec:java -Dexec.mainClass=easyagenda.seed.Seed -Dexec.args=--limpar  # apaga e popula de novo
 *
 * Os e-mails usam o domínio reservado .test: lembretes para eles nunca chegam a ninguém.
 * Só os dados destes usuários são mexidos; o resto do banco fica intacto.
 */
public final class Seed {

    public static final String SENHA = "easyagenda123";

    private static final String[][] PROFESSORES = {
            {"Carla Mendes", "carla.professora@easyagenda.test"},
            {"Rafael Souza", "rafael.professor@easyagenda.test"},
    };
    private static final String[][] ALUNOS = {
            {"Marina Lima", "marina.aluna@easyagenda.test"},
            {"Pedro Alves", "pedro.aluno@easyagenda.test"},
            {"Julia Rocha", "julia.aluna@easyagenda.test"},
    };

    private static final LocalDate HOJE = LocalDate.now(Horarios.FUSO);

    private Seed() {
    }

    public static void main(String[] args) {
        boolean limpar = args.length == 1 && "--limpar".equals(args[0]);
        if (args.length > 1 || (args.length == 1 && !limpar)) {
            System.err.println("Uso: Seed [--limpar]");
            System.exit(1);
        }

        String uri = env("MONGODB_URI", "mongodb://localhost:27017");
        String banco = env("MONGODB_DB", "easyagenda");
        Database.connect(uri, banco);
        System.out.println("Banco: " + banco + (uri.startsWith("mongodb+srv") ? " (Atlas)" : " (" + uri + ")"));
        Usuarios.garantirIndices();
        Aulas.garantirIndices();

        if (limpar) {
            limpar();
        } else if (Usuarios.buscarPorEmail(PROFESSORES[0][1]) != null) {
            System.out.println("O seed já foi aplicado neste banco. Para recriar: -Dexec.args=--limpar");
            imprimirCredenciais();
            Database.close();
            return;
        }

        popular();
        imprimirCredenciais();
        Database.close();
    }

    private static void popular() {
        ObjectId carla = criarUsuario(PROFESSORES[0], Usuarios.PROFESSOR);
        ObjectId rafael = criarUsuario(PROFESSORES[1], Usuarios.PROFESSOR);
        ObjectId marina = criarUsuario(ALUNOS[0], Usuarios.ALUNO);
        ObjectId pedro = criarUsuario(ALUNOS[1], Usuarios.ALUNO);
        ObjectId julia = criarUsuario(ALUNOS[2], Usuarios.ALUNO);

        Usuarios.definirPrazoCancelamento(carla, 24);
        Usuarios.definirPrazoCancelamento(rafael, 12);

        // ----- horários livres das próximas duas semanas -----
        int[] dias = {2, 3, 5, 7, 9, 12};
        for (int d : dias) {
            String dia = HOJE.plusDays(d).toString();
            Horarios.criar(carla, dia, "08:00", "09:00", Horarios.ONLINE);
            Horarios.criar(carla, dia, "09:00", "10:00", Horarios.PRESENCIAL);
            Horarios.criar(carla, dia, "14:00", "15:00", Horarios.ONLINE);
            Horarios.criar(rafael, dia, "10:00", "11:00", Horarios.PRESENCIAL);
            Horarios.criar(rafael, dia, "16:00", "17:00", Horarios.ONLINE);
            Horarios.criar(rafael, dia, "19:00", "20:00", Horarios.ONLINE);
        }

        // ----- aulas futuras, pelas regras do servidor de sockets -----
        agendar(carla, 2, "08:00", marina);
        agendar(carla, 3, "14:00", pedro);
        agendar(carla, 7, "09:00", julia);
        agendar(rafael, 2, "10:00", julia);
        agendar(rafael, 9, "19:00", pedro);

        // Marina marca com o Rafael e depois remarca (fica uma cancelada e uma agendada)
        String paraRemarcar = agendar(rafael, 5, "16:00", marina);
        exigir(OperacoesDeAula.remarcar(new PedidoDeRemarcacao(paraRemarcar,
                horario(rafael, 5, "19:00"), marina.toHexString(), Usuarios.ALUNO)), 201, "remarcar");

        // Pedro marca com a Carla e cancela (o horário volta a ficar livre)
        String paraCancelar = agendar(carla, 12, "08:00", pedro);
        exigir(OperacoesDeAula.cancelar(new PedidoDeCancelamento(paraCancelar,
                pedro.toHexString(), Usuarios.ALUNO)), 200, "cancelar");

        // ----- histórico: aulas que já passaram (gravadas direto no banco) -----
        aulaPassada(carla, marina, 14, "08:00", "09:00", Horarios.ONLINE, Aulas.REALIZADA);
        aulaPassada(carla, marina, 7, "08:00", "09:00", Horarios.ONLINE, Aulas.REALIZADA);
        aulaPassada(carla, pedro, 5, "09:00", "10:00", Horarios.PRESENCIAL, Aulas.REALIZADA);
        aulaPassada(rafael, julia, 6, "16:00", "17:00", Horarios.ONLINE, Aulas.REALIZADA);
        aulaPassada(rafael, julia, 3, "16:00", "17:00", Horarios.ONLINE, Aulas.REALIZADA);
        aulaPassada(rafael, pedro, 4, "10:00", "11:00", Horarios.PRESENCIAL, Aulas.CANCELADA);
        aulaPassada(carla, julia, 2, "14:00", "15:00", Horarios.ONLINE, Aulas.CANCELADA);

        System.out.println("Seed aplicado: 5 usuários, " + Horarios.colecao().countDocuments(
                Filters.in("professorId", List.of(carla, rafael))) + " horários, "
                + Aulas.colecao().countDocuments(Filters.in("professorId", List.of(carla, rafael))) + " aulas.");
    }

    /** Apaga só os usuários do seed e as aulas/horários deles. */
    private static void limpar() {
        List<ObjectId> ids = new ArrayList<>();
        for (String[] u : todos()) {
            Document usuario = Usuarios.buscarPorEmail(u[1]);
            if (usuario != null) {
                ids.add(usuario.getObjectId("_id"));
            }
        }
        long aulas = Aulas.colecao().deleteMany(Filters.or(
                Filters.in("professorId", ids), Filters.in("alunoId", ids))).getDeletedCount();
        long horarios = Horarios.colecao().deleteMany(Filters.in("professorId", ids)).getDeletedCount();
        long usuarios = Usuarios.colecao().deleteMany(Filters.in("_id", ids)).getDeletedCount();
        System.out.println("Removidos: " + usuarios + " usuários, " + horarios + " horários, " + aulas + " aulas.");
    }

    // ----- ajudantes -----

    private static ObjectId criarUsuario(String[] dados, String tipo) {
        return Usuarios.criar(dados[0], dados[1], SENHA, tipo).getObjectId("_id");
    }

    /** Id do horário livre do professor naquele dia (daqui a N dias) e hora. */
    private static String horario(ObjectId professor, int daquiADias, String inicio) {
        Document h = Horarios.colecao().find(Filters.and(
                Filters.eq("professorId", professor),
                Filters.eq("dia", HOJE.plusDays(daquiADias).toString()),
                Filters.eq("horaInicio", inicio))).first();
        if (h == null) {
            throw new IllegalStateException("horário não encontrado: +" + daquiADias + " dias " + inicio);
        }
        return h.getObjectId("_id").toHexString();
    }

    private static String agendar(ObjectId professor, int daquiADias, String inicio, ObjectId aluno) {
        Resultado r = OperacoesDeAula.agendar(
                new PedidoDeAgendamento(horario(professor, daquiADias, inicio), aluno.toHexString()));
        exigir(r, 201, "agendar");
        return Document.parse(r.getJson()).getString("id");
    }

    private static void exigir(Resultado resultado, int codigo, String acao) {
        if (resultado.getCodigo() != codigo) {
            throw new IllegalStateException("falha ao " + acao + ": " + resultado);
        }
    }

    /** Aula de N dias atrás, com o horário livre de onde ela saiu. */
    private static void aulaPassada(ObjectId professor, ObjectId aluno, int diasAtras,
                                    String inicio, String fim, String modalidade, String status) {
        LocalDate dia = HOJE.minusDays(diasAtras);
        Horarios.criar(professor, dia.toString(), inicio, fim, modalidade);
        Date dataHora = Date.from(LocalDateTime.of(dia, java.time.LocalTime.parse(inicio))
                .atZone(Horarios.FUSO).toInstant());
        Document aula = new Document()
                .append("professorId", professor)
                .append("alunoId", aluno)
                .append("dataHora", dataHora)
                .append("status", status)
                .append("modalidade", modalidade)
                .append("lembreteEnviado", true);
        if (Aulas.CANCELADA.equals(status)) {
            aula.append("canceladaEm", Date.from(dataHora.toInstant().minusSeconds(3 * 24 * 3600)));
        }
        Aulas.colecao().insertOne(aula);
    }

    private static List<String[]> todos() {
        List<String[]> lista = new ArrayList<>(List.of(PROFESSORES));
        lista.addAll(List.of(ALUNOS));
        return lista;
    }

    private static void imprimirCredenciais() {
        System.out.println("\nCredenciais (senha de todos: " + SENHA + ")");
        for (String[] p : PROFESSORES) {
            System.out.println("  professor  " + p[1] + "  (" + p[0] + ")");
        }
        for (String[] a : ALUNOS) {
            System.out.println("  aluno      " + a[1] + "  (" + a[0] + ")");
        }
    }

    private static String env(String nome, String padrao) {
        String valor = System.getenv(nome);
        return (valor == null || valor.isBlank()) ? padrao : valor;
    }
}
