package easyagenda.lembretes;

import java.util.Properties;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

/**
 * Envio de e-mail por SMTP com o Jakarta Mail (a mesma biblioteca que o
 * Spring Mail usa por baixo; aqui não temos Spring).
 *
 * Variáveis de ambiente: SMTP_HOST, SMTP_PORT (587), SMTP_USUARIO, SMTP_SENHA,
 * SMTP_REMETENTE (padrão: o SMTP_USUARIO) e SMTP_TLS (true).
 */
public final class EmailSmtp implements EnvioDeEmail {

    private static final String TEMPO_LIMITE_MS = "10000";

    private final Session sessao;
    private final String remetente;

    public EmailSmtp(String host, int porta, String usuario, String senha, String remetente, boolean tls) {
        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", String.valueOf(porta));
        props.put("mail.smtp.starttls.enable", String.valueOf(tls));
        props.put("mail.smtp.connectiontimeout", TEMPO_LIMITE_MS);
        props.put("mail.smtp.timeout", TEMPO_LIMITE_MS);
        props.put("mail.smtp.writetimeout", TEMPO_LIMITE_MS);

        Authenticator autenticador = null;
        if (usuario != null) {
            props.put("mail.smtp.auth", "true");
            autenticador = new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(usuario, senha);
                }
            };
        }
        this.sessao = Session.getInstance(props, autenticador);
        this.remetente = remetente;
    }

    static EmailSmtp doAmbiente(String host) {
        String usuario = env("SMTP_USUARIO", null);
        return new EmailSmtp(
                host,
                Integer.parseInt(env("SMTP_PORT", "587")),
                usuario,
                env("SMTP_SENHA", null),
                env("SMTP_REMETENTE", usuario != null ? usuario : "easyagenda@localhost"),
                Boolean.parseBoolean(env("SMTP_TLS", "true")));
    }

    @Override
    public void enviar(String para, String assunto, String texto) throws Exception {
        MimeMessage mensagem = new MimeMessage(sessao);
        mensagem.setFrom(new InternetAddress(remetente, "EasyAgenda", "UTF-8"));
        mensagem.setRecipients(Message.RecipientType.TO, InternetAddress.parse(para));
        mensagem.setSubject(assunto, "UTF-8");
        mensagem.setText(texto, "UTF-8");
        Transport.send(mensagem);
    }

    private static String env(String nome, String padrao) {
        String valor = System.getenv(nome);
        return (valor == null || valor.isBlank()) ? padrao : valor;
    }
}
