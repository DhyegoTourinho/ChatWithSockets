package Model;
import java.io.*;
import java.net.*;
import java.util.*;

public class ChatServer {
    private static final int PORT = 8080;
    private static Map<String, ClientHandler> clients = new HashMap<>();

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Servidor iniciado na porta " + PORT);

            while (true) {
                Socket socket = serverSocket.accept();
                ClientHandler clientHandler = new ClientHandler(socket);
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Envia mensagem para todos os conectados
    public static synchronized void broadcast(String message, ClientHandler excludeUser) {
        for (ClientHandler client : clients.values()) {
            if (client != excludeUser) {
                client.sendMessage(message);
            }
        }
    }

    // Envia mensagem privada
    public static synchronized void privateMessage(String targetUser, String message, ClientHandler sender) {
        ClientHandler target = clients.get(targetUser);
        if (target != null) {
            target.sendMessage("[Privado de " + sender.getUserName() + "]: " + message);
            sender.sendMessage("[Privado para " + targetUser + "]: " + message);
        } else {
            sender.sendMessage("Usuário '" + targetUser + "' não encontrado.");
        }
    }

    // Adiciona cliente
    public static synchronized void addClient(String userName, ClientHandler client) {
        clients.put(userName, client);
        broadcast(userName + " entrou no chat.", client);
    }

    // Remove cliente
    public static synchronized void removeClient(String userName, ClientHandler client) {
        clients.remove(userName);
        broadcast(userName + " saiu do chat.", client);
    }

    // Classe interna para lidar com cada cliente
    static class ClientHandler implements Runnable {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String userName;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public String getUserName() {
            return userName;
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                out.println("Digite seu nome:");
                userName = in.readLine();

                ChatServer.addClient(userName, this);
                System.out.println(userName + " conectado.");

                String message;
                while ((message = in.readLine()) != null) {
                    if (message.startsWith("/tell ")) {
                        // Sintaxe: /msg destinatario mensagem
                        String[] parts = message.split(" ", 3);
                        if (parts.length >= 3) {
                            String targetUser = parts[1];
                            String privateMsg = parts[2];
                            ChatServer.privateMessage(targetUser, privateMsg, this);
                        } else {
                            sendMessage("Formato inválido. Use: /msg nome mensagem");
                        }
                    } else {
                        // Broadcast
                        ChatServer.broadcast(userName + ": " + message, this);
                    }
                }
            } catch (IOException e) {
                System.out.println("Erro no cliente: " + e.getMessage());
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                ChatServer.removeClient(userName, this);
            }
        }

        public void sendMessage(String message) {
            out.println(message);
        }
    }
}
