package easyagenda.sockets;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/** Leitura do teclado, para o comando "desativar" do servidor (igual ao modelo da disciplina). */
public class Teclado {

    private static final BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in));

    /** Lê uma linha; devolve null se não houver teclado (ex.: rodando no Docker). */
    public static String getUmString() {
        String ret = null;
        try {
            ret = teclado.readLine();
        } catch (IOException erro) {
        } // sei que nao vai dar erro
        return ret;
    }
}
