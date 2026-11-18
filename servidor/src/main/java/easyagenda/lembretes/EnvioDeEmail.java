package easyagenda.lembretes;

/**
 * Quem manda o e-mail. Na aplicação é o SMTP (ou o console, se o SMTP não foi
 * configurado); nos testes é um carteiro falso que só anota o que receberia.
 */
public interface EnvioDeEmail {

    /** Envia um e-mail de texto. Lança exceção se não conseguir. */
    void enviar(String para, String assunto, String texto) throws Exception;

    /**
     * Escolhe o envio pelas variáveis de ambiente: com SMTP_HOST definida usa o
     * SMTP; sem ela, só escreve os e-mails no console (bom para desenvolver).
     */
    static EnvioDeEmail doAmbiente() {
        String host = System.getenv("SMTP_HOST");
        if (host == null || host.isBlank()) {
            System.out.println("[aviso] SMTP_HOST não definida; os lembretes serão só escritos no console.");
            return new EmailNoConsole();
        }
        return EmailSmtp.doAmbiente(host);
    }
}
