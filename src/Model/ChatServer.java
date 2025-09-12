package Model;
import java.io.*;
import java.net.*;
import java.util.*;

public class ChatServer {
    private static final int PORT = 8080;
    public static final String LISTUSERS = "/listUsers";
    public static final String PRIVATEMESSAGES = "/tell";
    public static final String HELP = "/help";
    public static final String GROUP = "/group";
    public static final String EXIT = "/exit";


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
    public static synchronized void privateMessage(String targetUser, String message, ClientHandler sender, boolean groupMessage) {
        ClientHandler target = clients.get(targetUser);
        String targetPrefixo = "Privado de ";
        String senderPrefixo = "Privado para ";
        if (groupMessage){
            targetPrefixo = "Mensagem em Grupo de ";
            senderPrefixo = "Mensagem em Grupo para ";
        }
        if (target != null) {
            target.sendMessage(targetPrefixo + sender.getUserName() + "]: " + message);
            sender.sendMessage(senderPrefixo + targetUser + "]: " + message);
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

        public void setUserName(BufferedReader in,  PrintWriter out) throws IOException {
            out.println("Digite seu nome:");
            userName = in.readLine();
            this.userName = userName;
        }

        public void ShowHelp (PrintWriter out) {
            out.println("=======| Lista de comandos |=======");
            out.println(" -----| Para ver usuarios logados |-----");
            out.println(LISTUSERS);
            out.println();
            out.println(" -----| Para enviar mensagens privadas |-----");
            out.println(PRIVATEMESSAGES);
            out.println();
            out.println(" -----| Para mensagem em grupo |-----");
            out.println(GROUP + " destinatario1" + " destinatario2" + " ... ");
            out.println();
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
                setUserName(in, out);
                if (userName.equals(" ") || userName.isEmpty() || userName.isBlank()) {
                    setUserName(in, out);
                }

                if (clients.containsKey(userName)) {
                    out.println("Nome do usuário indisponivel.");
                }

                ChatServer.addClient(userName, this);
                System.out.println(userName + " conectado.");

                String message;
                out.println("Parabens, você foi autenticado!");
                out.println("Digite /help para visualizar a lista de comandos.");
                while ((message = in.readLine()) != null) {
                    if (message.startsWith(HELP)){
                        ShowHelp(out);
                    }
                    if(message.startsWith(GROUP)){
                        List<String> Group = new ArrayList<>();
                        String[] parts = message.split(" ", 5);
                        for (int i = 1; i < parts.length; i++) {
                            Group.add(parts[i]);
                        }
                        out.println("=========================================");
                        out.println("Voce esta em um grupo com: ");
                        out.println(Group);
                        out.println("======| Digite '/exit' para sair do grupo |======");
                        while (!message.startsWith(EXIT)){
                            message = in.readLine();
                            for (String targetUser : Group) {
                            ChatServer.privateMessage(targetUser, message, this, true);
                            }
                        }
                    }
                    if (message.startsWith(LISTUSERS)){
                        out.println(" ======| Lista de usuarios |====== ");
                        int i = 0;
                        for (Map.Entry<String, ClientHandler> client : clients.entrySet()) {
                            i++;
                            out.println("#"+ i + " " + client.getValue().getUserName());
                        }
                    }
                    if (message.startsWith(PRIVATEMESSAGES)) {
                        String[] parts = message.split(" ", 3);
                        if (parts.length >= 3) {
                            String targetUser = parts[1];
                            String privateMsg = parts[2];
                            ChatServer.privateMessage(targetUser, privateMsg, this, false);
                        } else {
                            sendMessage("Formato invalido. Use: /tell destinatario mensagem");
                        }
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
