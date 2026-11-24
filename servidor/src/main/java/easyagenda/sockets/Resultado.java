package easyagenda.sockets;

import org.bson.Document;

/**
 * Resposta do servidor de sockets a um pedido.
 *
 * O código segue os mesmos números do HTTP (201 criado, 404 não encontrado,
 * 409 conflito...), assim a API só repassa para o site o que recebeu.
 * O corpo vai como texto JSON.
 */
public class Resultado extends Comunicado {

    private static final long serialVersionUID = 1L;

    private final int codigo;
    private final String json;

    public Resultado(int codigo, String json) {
        this.codigo = codigo;
        this.json = json;
    }

    public static Resultado ok(int codigo, Document corpo) {
        return new Resultado(codigo, corpo.toJson());
    }

    /** Erro no mesmo formato da API: {"erro": "mensagem"}. */
    public static Resultado erro(int codigo, String mensagem) {
        return ok(codigo, new Document("erro", mensagem));
    }

    public int getCodigo() {
        return this.codigo;
    }

    public String getJson() {
        return this.json;
    }

    @Override
    public String toString() {
        return this.codigo + " " + this.json;
    }
}
