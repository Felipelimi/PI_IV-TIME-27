package easyagenda.auth;

import org.bson.Document;
import org.bson.types.ObjectId;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Updates;

import easyagenda.Database;

/**
 * Tudo que mexe na coleção "usuarios" fica aqui.
 */
public final class Usuarios {

    public static final String PROFESSOR = "professor";
    public static final String ALUNO = "aluno";

    /** Prazo mínimo para o aluno cancelar ou remarcar, quando o professor não definiu outro (tarefa 4.1). */
    public static final int PRAZO_CANCELAMENTO_PADRAO_HORAS = 24;

    private Usuarios() {
    }

    public static MongoCollection<Document> colecao() {
        return Database.get().getCollection("usuarios");
    }

    /**
     * Garante o índice único de e-mail. O init.js já cria esse índice no banco
     * local, mas no Atlas (ou num banco de teste novo) ele pode não existir —
     * chamar isto na subida deixa os dois iguais. Rodar de novo não faz mal.
     */
    public static void garantirIndices() {
        colecao().createIndex(
                Indexes.ascending("email"),
                new IndexOptions().name("email_1").unique(true));
    }

    public static Document buscarPorEmail(String email) {
        return colecao().find(Filters.eq("email", email)).first();
    }

    /** Usuário pelo id, ou null se não existir. */
    public static Document buscarPorId(ObjectId id) {
        return colecao().find(Filters.eq("_id", id)).first();
    }

    /**
     * Com quantas horas de antecedência, no mínimo, o aluno pode cancelar ou
     * remarcar uma aula desse professor.
     */
    public static int prazoCancelamentoHoras(ObjectId professorId) {
        Document professor = buscarPorId(professorId);
        Object prazo = professor == null ? null : professor.get("prazoCancelamentoHoras");
        return prazo instanceof Number n ? n.intValue() : PRAZO_CANCELAMENTO_PADRAO_HORAS;
    }

    public static void definirPrazoCancelamento(ObjectId professorId, int horas) {
        colecao().updateOne(Filters.eq("_id", professorId), Updates.set("prazoCancelamentoHoras", horas));
    }

    /** Insere o usuário e devolve o documento salvo (já com o _id preenchido). */
    public static Document criar(String nome, String email, String senhaPura, String tipo) {
        Document usuario = new Document()
                .append("nome", nome)
                .append("email", email)
                .append("senha", Auth.criptografarSenha(senhaPura))
                .append("tipo", tipo);
        colecao().insertOne(usuario);
        return usuario;
    }

    /** Versão do usuário que pode ir para o cliente — sem a senha. */
    public static Document semSenha(Document usuario) {
        return new Document()
                .append("id", usuario.getObjectId("_id").toHexString())
                .append("nome", usuario.getString("nome"))
                .append("email", usuario.getString("email"))
                .append("tipo", usuario.getString("tipo"));
    }
}