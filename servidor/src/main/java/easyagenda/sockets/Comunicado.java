package easyagenda.sockets;

import java.io.ObjectInputFilter;
import java.io.Serializable;

/**
 * Tudo o que trafega entre a API e o servidor de sockets é um Comunicado
 * (mesmo modelo da disciplina): o objeto é serializado e vai pelo socket.
 */
public class Comunicado implements Serializable, Cloneable {

    private static final long serialVersionUID = 1L;

    /**
     * Quem lê do socket só aceita Comunicados deste pacote (e Strings/números
     * dentro deles). Sem esse filtro, qualquer um que conectasse na porta
     * poderia mandar um objeto Java qualquer para ser desserializado.
     */
    public static final ObjectInputFilter FILTRO = ObjectInputFilter.Config.createFilter(
            "maxdepth=5;maxbytes=65536;easyagenda.sockets.*;java.lang.*;!*");
}
