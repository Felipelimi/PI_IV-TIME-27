package easyagenda.auth;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.mindrot.jbcrypt.BCrypt;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.sun.net.httpserver.HttpExchange;

/**
 * Senha (BCrypt) e token (JWT).
 *
 * O segredo do JWT vem da variável de ambiente JWT_SECRET. Em produção ela é
 * obrigatória; em desenvolvimento/teste caímos em um valor padrão só para o
 * projeto rodar sem configuração extra.
 */
public final class Auth {

    private static final String EMISSOR = "easyagenda";
    private static final long HORAS_DE_VALIDADE = 8;

    private static final Algorithm ALGORITMO = Algorithm.HMAC256(segredo());

    private Auth() {
    }

    private static String segredo() {
        String valor = System.getenv("JWT_SECRET");
        if (valor == null || valor.isBlank()) {
            System.out.println("[aviso] JWT_SECRET não definida; usando segredo de desenvolvimento.");
            return "segredo-de-desenvolvimento-do-easyagenda";
        }
        return valor;
    }

    // ----- senha -----

    /** Gera o hash que vai para o banco. Nunca guardamos a senha pura. */
    public static String criptografarSenha(String senhaPura) {
        return BCrypt.hashpw(senhaPura, BCrypt.gensalt(12));
    }

    /** Confere a senha digitada contra o hash salvo. */
    public static boolean senhaConfere(String senhaPura, String hashSalvo) {
        if (hashSalvo == null || hashSalvo.isBlank()) {
            return false;
        }
        try {
            return BCrypt.checkpw(senhaPura, hashSalvo);
        } catch (IllegalArgumentException e) {
            return false; // hash com formato inválido
        }
    }

    // ----- token -----

    /** Dados que viajam dentro do token. */
    public record Usuario(String id, String tipo) {
    }

    public static String gerarToken(String usuarioId, String tipo) {
        Instant agora = Instant.now();
        return JWT.create()
                .withIssuer(EMISSOR)
                .withSubject(usuarioId)
                .withClaim("tipo", tipo)
                .withIssuedAt(agora)
                .withExpiresAt(agora.plus(HORAS_DE_VALIDADE, ChronoUnit.HOURS))
                .sign(ALGORITMO);
    }

    /** Valida a assinatura e o prazo do token. Devolve null se o token não presta. */
    public static Usuario validarToken(String token) {
        try {
            DecodedJWT decodificado = JWT.require(ALGORITMO)
                    .withIssuer(EMISSOR)
                    .build()
                    .verify(token);
            String tipo = decodificado.getClaim("tipo").asString();
            if (decodificado.getSubject() == null || tipo == null) {
                return null;
            }
            return new Usuario(decodificado.getSubject(), tipo);
        } catch (RuntimeException e) {
            return null; // assinatura inválida, token expirado ou mal formado
        }
    }

    /**
     * Lê o cabeçalho "Authorization: Bearer <token>" e devolve o usuário logado.
     * Null quando não há token ou o token é inválido.
     */
    public static Usuario usuarioDaRequisicao(HttpExchange exchange) {
        String cabecalho = exchange.getRequestHeaders().getFirst("Authorization");
        if (cabecalho == null || !cabecalho.startsWith("Bearer ")) {
            return null;
        }
        return validarToken(cabecalho.substring("Bearer ".length()).trim());
    }
}