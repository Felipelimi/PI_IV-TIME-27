package easyagenda.lembretes;

/**
 * "Envia" o e-mail escrevendo no console. Usado quando o SMTP não está
 * configurado, para o projeto rodar sem conta de e-mail.
 */
public final class EmailNoConsole implements EnvioDeEmail {

    @Override
    public void enviar(String para, String assunto, String texto) {
        System.out.println("""
                ----- e-mail (não enviado: SMTP não configurado) -----
                Para: %s
                Assunto: %s

                %s
                ------------------------------------------------------""".formatted(para, assunto, texto));
    }
}
