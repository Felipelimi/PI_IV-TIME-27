package easyagenda.auth;

import org.bson.Document;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;

import easyagenda.Database;

/**
 * Tudo que mexe na coleção "usuarios" fica aqui.
 */
public final class Usuarios {

    public static final String PROFESSOR = "professor";
    public static final String ALUNO = "aluno";

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