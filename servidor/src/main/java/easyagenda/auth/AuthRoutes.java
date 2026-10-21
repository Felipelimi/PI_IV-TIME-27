package easyagenda.auth;

import java.io.IOException;
import java.util.Locale;
import java.util.regex.Pattern;

import org.bson.Document;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.sun.net.httpserver.HttpExchange;

import easyagenda.Json;

/**
 * Rotas de cadastro e login.
 */
public final class AuthRoutes{

    private static final Pattern EMAIL_VALIDO = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int TAMANHO_MINIMO_DA_SENHA = 6;

    private AuthRoutes() {
    }

    /**
     * POST /cadastro
     * Recebe: {"nome", "email", "senha", "tipo"}  (tipo = professor | aluno)
     * Devolve 201 com o usuário criado (sem a senha).
     * 400 campo faltando/inválido, 409 e-mail já cadastrado.
     */
    public static void cadastrar(HttpExchange exchange) throws IOException {
        Document corpo = Json.lerCorpo(exchange);
        if (corpo == null) {
            Json.erro(exchange, 400, "envie um JSON com nome, email, senha e tipo");
            return;
        }

        String nome = Json.texto(corpo, "nome");
        String email = Json.texto(corpo, "email");
        String senha = Json.texto(corpo, "senha");
        String tipo = Json.texto(corpo, "tipo");

        if (nome == null || email == null || senha == null || tipo == null) {
            Json.erro(exchange, 400, "nome, email, senha e tipo são obrigatórios");
            return;
        }
        email = email.toLowerCase(Locale.ROOT);
        if (!EMAIL_VALIDO.matcher(email).matches()) {
            Json.erro(exchange, 400, "e-mail inválido");
            return;
        }
        if (senha.length() < TAMANHO_MINIMO_DA_SENHA) {
            Json.erro(exchange, 400, "a senha precisa ter pelo menos " + TAMANHO_MINIMO_DA_SENHA + " caracteres");
            return;
        }
        if (!Usuarios.PROFESSOR.equals(tipo) && !Usuarios.ALUNO.equals(tipo)) {
            Json.erro(exchange, 400, "tipo deve ser 'professor' ou 'aluno'");
            return;
        }

        // Checagem amigável...
        if (Usuarios.buscarPorEmail(email) != null) {
            Json.erro(exchange, 409, "e-mail já cadastrado");
            return;
        }

        Document criado;
        try {
            criado = Usuarios.criar(nome, email, senha, tipo);
        } catch (MongoWriteException e) {
            // ...e a garantia de verdade: o índice único do banco, caso dois
            // cadastros com o mesmo e-mail cheguem ao mesmo tempo.
            if (e.getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
                Json.erro(exchange, 409, "e-mail já cadastrado");
                return;
            }
            throw e;
        }

        Json.enviar(exchange, 201, Usuarios.semSenha(criado));
    }

    /**
     * POST /login
     * Recebe: {"email", "senha"}
     * Devolve 200 com {"token", "usuario"} ou 401 se não bater.
     */
    public static void entrar(HttpExchange exchange) throws IOException {
        Document corpo = Json.lerCorpo(exchange);
        if (corpo == null) {
            Json.erro(exchange, 400, "envie um JSON com email e senha");
            return;
        }

        String email = Json.texto(corpo, "email");
        String senha = Json.texto(corpo, "senha");
        if (email == null || senha == null) {
            Json.erro(exchange, 400, "email e senha são obrigatórios");
            return;
        }

        Document usuario = Usuarios.buscarPorEmail(email.toLowerCase(Locale.ROOT));
        // Mesma mensagem para e-mail inexistente e senha errada: não contamos
        // para quem está tentando adivinhar quais e-mails existem.
        if (usuario == null || !Auth.senhaConfere(senha, usuario.getString("senha"))) {
            Json.erro(exchange, 401, "e-mail ou senha inválidos");
            return;
        }

        String token = Auth.gerarToken(usuario.getObjectId("_id").toHexString(), usuario.getString("tipo"));
        Json.enviar(exchange, 200, new Document()
                .append("token", token)
                .append("usuario", Usuarios.semSenha(usuario)));
    }
}