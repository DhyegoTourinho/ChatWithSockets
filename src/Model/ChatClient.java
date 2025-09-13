package Model;

import java.io.*;
import java.net.*;

public class ChatClient {
    //Mude para ajustar o servidor
    private static final String SERVER_IP = "127.0.0.1";
    //private static final String SERVER_IP = "IP_DO_SERVIDOR, PRECISA SER A MAQUINA HOSTEANDO";
    private static final int SERVER_PORT = 8080;

    public static void main(String[] args) {
        try (Socket socket = new Socket(SERVER_IP, SERVER_PORT)) {
            System.out.println(SERVER_IP);
            System.out.println("Conectado ao servidor");

            BufferedReader keyboard = new BufferedReader(new InputStreamReader(System.in));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Thread para ouvir mensagens do servidor
            new Thread(() -> {
                try {
                    String response;
                    while ((response = in.readLine()) != null) {
                        System.out.println(response);
                    }
                } catch (IOException e) {
                    System.out.println("Conexão encerrada.");
                }
            }).start();

            // Enviar mensagens digitadas sempre que apertar "ENTER"
            String text;
            while ((text = keyboard.readLine()) != null) {
                out.println(text);
            }
            //Tratativa para informar que o servidor caiu.
        } catch (ConnectException e){
            System.out.println("\nO servidor se encontra desligado, ou sua porta está incorreta;\nColoque uma porta disponivel na maquina do servidor para se conectar");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
