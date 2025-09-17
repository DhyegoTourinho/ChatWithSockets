package Model;
import java.io.*;
import java.net.*;
import java.util.*;

//TODO: Fazer endereçamento IP. (FEITO)
//TODO: Fazer mensagem descritiva para os comandos. (FEITO)
//TODO: Não enviar mensagem para si mesmo. (FEITO)
//TODO: Deixar intuitivo que você está mandando msg em grupo (FEITO)
//TODO: Fazer tratativa para grupos serem sempre 1-N. (FEITO)
//TODO: Fazer usuário ser encontrado a partir de um toEqualsIgnoreCase(). (FEITO)
//TODO: Envio de arquivo (URGENTE).
//TODO: Implementar aceite para iniciar comunicação em grupo. (OPCIONAL)
//TODO: Nome de grupo, mais interface para utilizar (OPCIONAL)

public class ChatServer {
    private static final int PORT = 8080;
    public static final String LISTUSERS = "/listUsers";
    public static final String PRIVATEMESSAGES = "/tell";
    public static final String HELP = "/help";
    public static final String GROUP = "/group";
    public static final String EXIT = "/exit";
    public static final String SENDFILE = "/SendFile";
    public static final String MESSAGEFORALL = "/all";
    public static final String WARNINGGROUP = "WarningGroup";

    private static final Map<String, ClientHandler> clients = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

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
            target.sendMessage("[" + targetPrefixo + sender.getUserName() + "]: " + message);
            sender.sendMessage("[" + senderPrefixo + targetUser + "]: " + message);
        } else {
            sender.sendMessage("Usuário '" + targetUser + "' não encontrado.");
        }
    }

    // Envia Aviso individual.
    public static synchronized void WarningMessage(String targetUser, String type, ClientHandler sender, List<String> Group) {
        ClientHandler target = clients.get(targetUser);
        if (target != null) {
            if (type.equalsIgnoreCase(WARNINGGROUP)) {
            target.sendMessage("======|" + sender.getUserName() + " adicionou voce em um grupo |======");
            target.sendMessage(sender.getUserName() + " " + Group.toString());
            }
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

    //Parte de envio de arquivo
//    public static void SendFile(Socket socket, String arquivo) {
//        try {
//            DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream());
//            File file = new File(arquivo);
//            FileInputStream fileInputStream = new FileInputStream(file);
//
//            // Envia o tamanho do arquivo primeiro
//            dataOutputStream.writeLong(file.length());
//
//            byte[] buffer = new byte[4096];
//            int bytesRead;
//            while ((bytesRead = fileInputStream.read(buffer)) != -1) {
//                dataOutputStream.write(buffer, 0, bytesRead);
//            }
//
//            dataOutputStream.flush();
//            fileInputStream.close();
//
//            System.out.println("Arquivo enviado com sucesso.");
//        } catch (FileNotFoundException e) {
//            System.out.println("Erro: Arquivo não encontrado. " + e.getMessage());
//        } catch (IOException e) {
//            System.out.println("Erro de I/O: " + e.getMessage());
//        }
//    }
//
//    public static void receiveFile(Socket socket, String destino) {
//        try {
//            DataInputStream dataInputStream = new DataInputStream(socket.getInputStream());
//            String fileName = dataInputStream.readUTF();   // nome do arquivo
//            long fileSize = dataInputStream.readLong();    // tamanho do arquivo
//
//            File dir = new File(destino);
//            if (!dir.exists()) dir.mkdirs();
//
//            FileOutputStream fos = new FileOutputStream(new File(dir, fileName));
//
//            byte[] buffer = new byte[4096];
//            int bytesRead;
//            long totalRead = 0;
//
//            while (totalRead < fileSize &&
//                    (bytesRead = dataInputStream.read(buffer, 0, (int)Math.min(buffer.length, fileSize - totalRead))) != -1) {
//                fos.write(buffer, 0, bytesRead);
//                totalRead += bytesRead;
//            }
//
//            fos.close();
//            System.out.println("Arquivo recebido: " + fileName);
//        } catch (IOException e) {
//            System.out.println("Erro ao receber arquivo: " + e.getMessage());
//        }
//    }


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
            this.userName = in.readLine();
        }

        public void ShowHelp (PrintWriter out) {
            out.println("======| Lista de comandos |======");
            out.println("======| Para ver usuarios logados |======");
            out.println(LISTUSERS + " destinatario" + " Mensagem");
            out.println();
            out.println("======| Para enviar mensagens privadas |======");
            out.println(PRIVATEMESSAGES + " destinatario" + " Mensagem");
            out.println();
            out.println("======| Para mensagem em grupo |======");
            out.println(GROUP + " destinatario1" + " destinatario" + " ... " + " [MAX:5]");
            out.println();
        }

        @Override
        public void run() {
            try {

                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
                do {
                    setUserName(in, out);
                    if (clients.containsKey(userName)) {
                        out.println("Nome do usuário indisponivel.");
                    }
                    if (userName.equals(" ") || userName.isEmpty() || userName.isBlank()){
                        out.println("Nome Invalido.");
                    }
                } while (userName.equals(" ") || userName.isEmpty() || userName.isBlank() || clients.containsKey(userName));

                if (clients.containsKey(userName)) {
                    out.println("Nome do usuário indisponivel.");
                }

                ChatServer.addClient(userName, this);
                System.out.println(userName + " conectado.");

                String message;
                out.println("Parabens, você foi autenticado!");
                out.println("Digite /help para visualizar a lista de comandos.");
                while ((message = in.readLine()) != null) {
                    if (message.toLowerCase().startsWith(HELP.toLowerCase())){
                        ShowHelp(out);
                    }

                    if (message.toLowerCase().startsWith(LISTUSERS.toLowerCase())){
                        out.println("==========| Lista de usuarios |======");
                        int i = 0;
                        for (Map.Entry<String, ClientHandler> client : clients.entrySet()) {
                            i++;
                            out.println("#"+ i + " " + client.getValue().getUserName());
                        }
                    }

                    if (message.toLowerCase().startsWith(PRIVATEMESSAGES.toLowerCase())) {
                        String[] parts = message.split(" ", 3);
                        if (parts.length >= 3) {
                            String targetUser = parts[1];
                            String privateMsg = parts[2];
                            if (!(targetUser.equalsIgnoreCase(userName))) {
                                ChatServer.privateMessage(targetUser, privateMsg, this, false);
                            } else {
                                out.println("Escolha um destinatario que nao seja voce.");
                            }
                        } else {
                            sendMessage("Formato invalido. Use: /tell destinatario mensagem");
                        }
                    }

                    if(message.toLowerCase().startsWith(GROUP.toLowerCase())){
                        boolean InGroup = true;
                        List<String> Group = new ArrayList<>();
                        String[] parts = message.split(" ", 5);
                        //Impede que seja criado um grupo somente com o usuário.
                        if (parts.length == 1){
                            out.println("Formato invalido, defina pelo menos 1 destinatario");
                            out.println(GROUP + " destinatario1" + " destinatario" + " ... " + " [MAX:5]");
                        } else if (parts.length <= 2 && parts[1].equalsIgnoreCase(userName)) {
                            out.println("Nao eh possivel criar um grupo com somente voce.");
                        }else {
                            for (int i = 1; i < parts.length; i++) {
                                if (!parts[i].equalsIgnoreCase(userName)) {
                                    Group.add(parts[i]);
                                    ChatServer.WarningMessage(parts[i], WARNINGGROUP, this, Group);
                                }
                            }
                            out.println("=================================================");
                            out.println("======| Digite '/exit' para sair do grupo |======");
                            out.println("Voce esta em um grupo com: ");
                            out.println(Group);
                            while (InGroup) {
                                message = in.readLine();
                                if(message.toLowerCase().startsWith(EXIT)) {
                                    out.println("============| Você saiu do grupo |============");
                                    InGroup = false;
                                } else {
                                    for (String targetUser : Group) {
                                        ChatServer.privateMessage(targetUser, message, this, true);
                                    }
                                }
                            }
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