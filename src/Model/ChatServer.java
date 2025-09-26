package Model;
//ChatServer.java
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServer {
    private static final int PORT = 8080;
    public static final String LISTUSERS = "/listusers";
    public static final String PRIVATEMESSAGES = "/tell";
    public static final String HELP = "/help";
    public static final String GROUP = "/group";
    public static final String EXIT = "/exit";
    public static final String JOINGROUP = "/joingroup";
    public static final String SENDFILE = "/sendfile";
    public static final String GETFILE = "/getfile";
    public static final String SENDFILETO = "/sendfileto";

    // Lista de grupos sincronizada
    private static final List<Group> groups = Collections.synchronizedList(new ArrayList<>());

    private static final Map<String, ClientHandler> clients = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private static final FileTransferManager fileTransferManager = new FileTransferManager();

    public static void main(String[] args) {
        System.out.println("Servidor iniciado na porta " + PORT);
        new Thread(fileTransferManager).start();
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket socket = serverSocket.accept();
                ClientHandler clientHandler = new ClientHandler(socket);
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Métodos de controle de clientes
    public static synchronized void broadcast(String message, ClientHandler excludeUser) {
        for (ClientHandler client : clients.values()) {
            if (client != excludeUser) {
                client.sendMessage(message);
            }
        }
    }

    // Método para mensagens privadas e em grupo
    public static synchronized void privateMessage(String targetUser, String message, ClientHandler sender, boolean groupMessage) {
        // Envio para o alvo
        ClientHandler target = getClientByName(targetUser);
        String targetPrefixo = groupMessage ? "Mensagem de " : "Privado de ";

        if (target != null) {
            target.sendMessage("[" + targetPrefixo + sender.getUserName() + "]: " + message);
        } else if (!groupMessage) {
            // Se não for mensagem de grupo, notifica o remetente sobre o erro
            sender.sendMessage("Usuário '" + targetUser + "' não encontrado.");
        }
    }

    // Envia mensagem SÓ para os membros do grupo (opcionalmente excluindo um)
    public static synchronized void sendGroupNotification(Group group, String message, ClientHandler excludeUser) {
        for (String member : group.getGroupMembers()) {
            ClientHandler target = getClientByName(member);
            // Verifica se o alvo está conectado E se não é o usuário a ser excluído
            if (target != null && target != excludeUser) {
                target.sendMessage("[Grupo " + group.getName() + "] ");
            }
        }
    }

    // NOTIFICAÇÃO DE CONVITE: Enviada apenas para o targetUser
    public static synchronized void WarningMessage(String targetUser, ClientHandler sender, Group group) {
        ClientHandler target = getClientByName(targetUser);
        if (target != null) {
            target.sendMessage("======|" + sender.getUserName() + " convidou voce para o grupo " + group.getName() + "|======");
            target.sendMessage(group.toString());
            target.sendMessage("Para entrar, digite: " + ChatServer.JOINGROUP + " " + group.getName());
        }
    }

    public static synchronized void addClient(String userName, ClientHandler client) {
        clients.put(userName, client);
        // Remove broadcast de entrada de usuário se o objetivo é só chat privado/grupo
        // broadcast(userName + " entrou no chat.", client);
        System.out.println("Aviso: " + userName + " entrou no chat.");
    }

    public static synchronized void removeClient(String userName, ClientHandler client) {
        clients.remove(userName);
        // Remove broadcast de saída de usuário
        // broadcast(userName + " saiu do chat.", client);
        System.out.println("Aviso: " + userName + " saiu do chat.");
    }

    // --- MÉTODOS PÚBLICOS "PORTEIROS" PARA ACESSO SEGURO À LISTA 'clients' ---
    public static synchronized ClientHandler getClientByName(String name) {
        return clients.get(name);
    }

    public static synchronized boolean isUserConnected(String userName) {
        return clients.containsKey(userName);
    }

    public static synchronized Set<String> getConnectedUserNames() {
        return new HashSet<>(clients.keySet());
    }

    // --- CLASSE INTERNA ÚNICA E CORRETA PARA O CLIENTE ---
    static class ClientHandler implements Runnable {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String userName;

        public ClientHandler(Socket socket) { this.socket = socket; }
        public String getUserName() { return userName; }

        public void setUserName(BufferedReader in, PrintWriter out) throws IOException {
            out.println("Digite seu nome:");
            this.userName = in.readLine();
        }

        public void ShowHelp(PrintWriter out) {
            out.println("======| Lista de comandos |======");
            out.println(ChatServer.LISTUSERS + " -> Ver usuarios logados");
            out.println(ChatServer.PRIVATEMESSAGES + " <destinatario> <Mensagem> -> Enviar mensagem privada");
            out.println(ChatServer.SENDFILETO + " <destinatario>  <caminho_completo_do_arquivo> -> Enviar um arquivo para o destinatario");
            out.println(ChatServer.GETFILE + " <ID_do_arquivo> -> Baixar um arquivo recebido");
            out.println(ChatServer.GROUP + " <nome do grupo> <usuario1> <usuario2> ... -> Criar um grupo de chat [MAX 5]");
            out.println(ChatServer.JOINGROUP + " <nome do grupo> -> Entra no grupo.");
            out.println("======| Comandos em Grupo |======");
            out.println("Qualquer mensagem (não comando) é enviada para o grupo.");
            out.println(ChatServer.SENDFILE + " <caminho_completo_do_arquivo> -> Enviar um arquivo para o grupo");
            out.println(ChatServer.GETFILE + " <ID_do_arquivo> -> Baixar um arquivo recebido");
            out.println(ChatServer.EXIT + " -> Sair do grupo e retornar ao chat geral.");
        }

        public void GroupCommunication(Group groupAux) throws IOException {
            String message;
            out.println("============| Você entrou no grupo '" + groupAux.getName() + "'. Digite " + ChatServer.EXIT + " para sair. |============");

            // Notifica os outros membros (exceto o próprio) sobre a entrada
            ChatServer.sendGroupNotification(groupAux, this.userName + " entrou no grupo.", this);

            boolean inGroup = true;
            while (inGroup) {
                message = in.readLine();

                if (message == null || message.equalsIgnoreCase(ChatServer.EXIT)) {
                    out.println("============| Você saiu do grupo |============");
                    inGroup = false;
                    groupAux.remove(this.userName);

                    // Notifica os outros membros sobre a saída (excluindo ninguem, pois o usuário já saiu)
                    ChatServer.sendGroupNotification(groupAux, this.userName + " saiu do grupo.", null);

                    // Se o grupo ficar vazio, remove ele da lista global.
                    if (groupAux.getGroupMembers().isEmpty()) {
                        groups.remove(groupAux);
                        System.out.println("Grupo '" + groupAux.getName() + "' removido por estar vazio.");
                    }

                } else {
                    if (message.toLowerCase().startsWith(ChatServer.SENDFILE)) {
                        String[] fileParts = message.split(" ", 2);
                        if (fileParts.length == 2) {
                            File file = new File(fileParts[1]);
                            if (file.exists() && !file.isDirectory()) {
                                fileTransferManager.requestUpload(this, groupAux.groupMembers, file);
                            } else {
                                out.println("ERRO: Arquivo não encontrado ou é um diretório.");
                            }
                        } else {
                            out.println("Uso inválido. Use: /sendfile <caminho_completo_do_arquivo>");
                        }
                    } else if (message.toLowerCase().startsWith(ChatServer.GETFILE)) {
                        String[] getParts = message.split(" ", 2);
                        if (getParts.length == 2){
                            fileTransferManager.requestDownload(this, getParts[1]);
                        }
                        else {
                            out.println("Uso inválido. Use: /getfile <ID_do_arquivo>");
                        }
                    }
                    // ENVIAR MENSAGEM DE TEXTO EM GRUPO
                    else {
                        // Envia para todos os membros (eles receberão a mensagem)
                        for (String targetUser : groupAux.groupMembers) {
                            ChatServer.privateMessage(targetUser, message, this, true);
                        }
                        // Confirmação para o próprio remetente
                        out.println("[Grupo: " + groupAux.getName() + "] ");
                    }
                }
            }
        };

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                do {
                    setUserName(in, out);
                    if (ChatServer.isUserConnected(userName)) {
                        out.println("Nome do usuário indisponivel.");
                    }
                    if (userName == null || userName.trim().isEmpty()) {
                        out.println("Nome Invalido.");
                        userName = "";
                    }
                } while (userName.isEmpty() || ChatServer.isUserConnected(userName));

                ChatServer.addClient(userName, this);
                System.out.println(userName + " conectado.");
                out.println("Parabens, você foi autenticado!");
                out.println("Digite /help para visualizar a lista de comandos.");

                String message;
                while ((message = in.readLine()) != null) {
                    if (message.toLowerCase().startsWith(ChatServer.HELP)) {
                        ShowHelp(out);
                    } else if (message.toLowerCase().startsWith(ChatServer.LISTUSERS)) {
                        out.println("==========| Lista de usuarios |======");
                        int i = 0;
                        for (String currentUserName : ChatServer.getConnectedUserNames()) {
                            i++;
                            out.println("#" + i + " " + currentUserName);
                        }
                    } else if (message.toLowerCase().startsWith(ChatServer.PRIVATEMESSAGES)) {
                        String[] parts = message.split(" ", 3);
                        if (parts.length >= 3) {
                            String targetUser = parts[1];
                            String privateMsg = parts[2];
                            if (!targetUser.equalsIgnoreCase(userName)) {
                                ChatServer.privateMessage(targetUser, privateMsg, this, false);
                                // Confirmação para o remetente da mensagem privada
                                out.println("[Privado para " + targetUser + "]: " + privateMsg);
                            } else {
                                out.println("Você não pode enviar uma mensagem privada para si mesmo.");
                            }
                        } else {
                            sendMessage("Formato inválido. Use: /tell <destinatario> <mensagem>");
                        }
                    } else if (message.toLowerCase().startsWith(ChatServer.GETFILE)){
                        String[] parts = message.split(" ", 2);
                        if (parts.length == 2){
                            fileTransferManager.requestDownload(this, parts[1]);
                        }
                        else {
                            out.println("Uso inválido. Use: /getfile <ID_do_arquivo>");
                        }
                    }
                    else if (message.toLowerCase().startsWith(SENDFILETO)) {
                        String[] parts = message.split(" ", 3);
                        if (parts.length == 3) {
                            String targetUser = parts[1];
                            File file = new File(parts[2]);
                            if (file.exists() && !file.isDirectory()) {
                                if (ChatServer.isUserConnected(targetUser)) {
                                    List<String> recipient = Collections.singletonList(targetUser);
                                    fileTransferManager.requestUpload(this, recipient, file);
                                } else {
                                    out.println("Usuário '" + targetUser + "' não está online.");
                                }
                            } else {
                                out.println("ERRO: Arquivo não encontrado ou é um diretório.");
                            }
                        } else {
                            out.println("Uso inválido. Use: /sendfileto <usuario> <caminho_completo_do_arquivo>");
                        }
                    }
                    // LÓGICA DE ENTRAR NO GRUPO
                    else if (message.toLowerCase().startsWith(JOINGROUP)) {
                        String[] parts = message.split(" ");
                        if (parts.length == 2) {
                            String groupName = parts[1];
                            Group groupAux = groups.stream()
                                    .filter(g -> g.getName().equalsIgnoreCase(groupName))
                                    .findFirst()
                                    .orElse(null);
                            if (groupAux == null) {
                                out.println("Grupo " + groupName + " nao encontrado");
                                continue;
                            }

                            if (!groupAux.isMember(userName)) {
                                groupAux.add(userName);
                            }

                            GroupCommunication(groupAux);

                        } else {
                            out.println("Formato invalido. Tente: /joingroup <Nome do Grupo>");
                        }

                    }
                    // LÓGICA DE CRIAR GRUPO
                    else if (message.toLowerCase().startsWith(ChatServer.GROUP)) {
                        String[] parts = message.split(" ");
                        if (parts.length < 2) {
                            out.println("Formato inválido. Use: /group <nome> <usuario1>...");
                            continue;
                        }
                        if (parts.length > 7) {
                            out.println("Limite de 5 usuários (além do criador) por grupo excedido.");
                            continue;
                        }

                        String name = parts[1];
                        if (groups.stream().anyMatch(g -> g.getName().equalsIgnoreCase(name))) {
                            out.println("Grupo '" + name + "' já existe. Escolha outro nome.");
                            continue;
                        }

                        Group groupAux = new Group(name, userName);

                        // Adiciona os membros convidados
                        for (int i = 2; i < parts.length; i++) {
                            String targetUser = parts[i];
                            if (!targetUser.equalsIgnoreCase(userName) && ChatServer.isUserConnected(targetUser)) {
                                groupAux.add(targetUser);
                            } else if (!ChatServer.isUserConnected(targetUser)) {
                                out.println("Aviso: Usuário '" + targetUser + "' não encontrado.");
                            }
                        }

                        if (groupAux.groupMembers.size() <= 1) {
                            out.println("O grupo precisa de pelo menos mais um usuário online para ser criado. Excluindo grupo...");
                        } else {
                            groups.add(groupAux);

                            // Avisa os convidados
                            for (String member : groupAux.groupMembers) {
                                if (!member.equalsIgnoreCase(userName)) {
                                    ChatServer.WarningMessage(member, this, groupAux);
                                }
                            }

                            out.println("=================================================");
                            out.println("Voce criou o grupo '" + name + "' com: " + groupAux.groupMembers.toString());
                            GroupCommunication(groupAux); // Criador entra automaticamente
                        }
                    }
                    // *** CORREÇÃO: Remove o BROADCAST GERAL neste 'else' final ***
                    else {
                        // Trata qualquer outra entrada como comando inválido no contexto do chat principal.
                        out.println("Comando ou formato inválido. Digite /help para ver a lista de comandos.");
                    }
                }
            } catch (IOException e) {
                System.out.println("Conexão com " + (userName != null ? userName : "cliente") + " perdida.");
            } finally {
                if (userName != null) {
                    ChatServer.removeClient(userName, this);
                }
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        public void sendMessage(String message) { out.println(message); }
    }
}

// --- CLASSES AUXILIARES (Sem Alterações) ---

class FileTransferManager implements Runnable {
    private final Map<String, FileInfo> pendingFiles = new ConcurrentHashMap<>();
    private final File tempDir = new File("server_temp_files");

    public FileTransferManager() {
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }
    }

    public void requestUpload(ChatServer.ClientHandler sender, List<String> recipients, File file) {
        String fileId = UUID.randomUUID().toString().substring(0, 8);
        String fileName = file.getName();
        long fileSize = file.length();
        try {
            ServerSocket serverSocket = new ServerSocket(0);
            int port = serverSocket.getLocalPort();
            FileInfo info = new FileInfo(fileId, fileName, fileSize, sender.getUserName(), recipients, serverSocket);
            pendingFiles.put(fileId, info);
            sender.sendMessage("UPLOAD_READY|" + fileId + "|" + port);
            System.out.println("Servidor pronto para receber " + fileName + " de " + sender.getUserName() + " na porta " + port);
        } catch (IOException e) {
            sender.sendMessage("ERRO: Não foi possível iniciar o servidor de arquivos.");
            e.printStackTrace();
        }
    }

    public void requestDownload(ChatServer.ClientHandler downloader, String fileId) {
        FileInfo info = pendingFiles.get(fileId);
        if (info != null && info.getTempFilePath() != null) {
            try {
                ServerSocket serverSocket = new ServerSocket(0);
                int port = serverSocket.getLocalPort();
                info.setDownloadSocket(serverSocket);
                downloader.sendMessage("DOWNLOAD_READY|" + info.getFileName() + "|" + info.getFileSize() + "|" + port);
                System.out.println("Servidor pronto para enviar " + info.getFileName() + " para " + downloader.getUserName() + " na porta " + port);
            } catch (IOException e) {
                downloader.sendMessage("ERRO: Não foi possivel iniciar o envio do arquivo.");
                e.printStackTrace();
            }
        } else {
            downloader.sendMessage("ERRO: ID de arquivo invalido ou o arquivo não está mais disponivel.");
        }
    }

    @Override
    public void run() {
        while (true) {
            try {
                for (FileInfo info : pendingFiles.values()) {
                    if (info.getUploadSocket() != null && !info.getUploadSocket().isClosed()) {
                        handleUpload(info);
                    }
                    if (info.getDownloadSocket() != null && !info.getDownloadSocket().isClosed()) {
                        handleDownload(info);
                    }
                }
                Thread.sleep(100);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void handleUpload(FileInfo info) {
        new Thread(() -> {
            try (Socket clientSocket = info.getUploadSocket().accept()) {
                info.getUploadSocket().close();
                System.out.println("Cliente " + info.getSender() + " conectado para upload do arquivo " + info.getFileName());
                DataInputStream dis = new DataInputStream(clientSocket.getInputStream());
                File tempFile = new File(tempDir, info.getFileId() + "_" + info.getFileName());
                try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    long totalRead = 0;
                    while (totalRead < info.getFileSize() && (bytesRead = dis.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                        totalRead += bytesRead;
                    }
                }
                info.setTempFilePath(tempFile.getAbsolutePath());
                System.out.println("Arquivo " + info.getFileName() + " recebido com sucesso.");
                String notification = "FILE_NOTIFICATION|" + info.getSender() + "|" + info.getFileName() + "|" + info.getFileId();
                for (String recipientName : info.getRecipients()) {
                    ChatServer.ClientHandler recipientClient = ChatServer.getClientByName(recipientName);
                    if (recipientClient != null) {
                        recipientClient.sendMessage(notification);
                    }
                }
            } catch (IOException e) {
                System.err.println("Erro no upload do arquivo " + info.getFileName() + ": " + e.getMessage());
            } finally {
                info.closeUploadSocket();
            }
        }).start();
    }

    private void handleDownload(FileInfo info) {
        new Thread(() -> {
            try (Socket clientSocket = info.getDownloadSocket().accept()) {
                info.getDownloadSocket().close();
                System.out.println("Cliente conectado para download do arquivo " + info.getFileName());
                File fileToSend = new File(info.getTempFilePath());
                try (FileInputStream fis = new FileInputStream(fileToSend);
                     DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream())) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        dos.write(buffer, 0, bytesRead);
                    }
                    dos.flush();
                }
                System.out.println("Arquivo " + info.getFileName() + " enviado com sucesso.");
            } catch (IOException e) {
                System.err.println("Erro no download do arquivo " + info.getFileName() + ": " + e.getMessage());
            } finally {
                info.closeDownloadSocket();
            }
        }).start();
    }
}

class FileInfo {
    private final String fileId, fileName, sender;
    private String tempFilePath;
    private final long fileSize;
    private final List<String> recipients;
    private final ServerSocket uploadSocket;
    private ServerSocket downloadSocket;

    public FileInfo(String fileId, String fileName, long fileSize, String sender, List<String> recipients, ServerSocket uploadSocket) {
        this.fileId = fileId; this.fileName = fileName; this.fileSize = fileSize;
        this.sender = sender; this.recipients = recipients; this.uploadSocket = uploadSocket;
    }

    public String getFileId() { return fileId; }
    public String getFileName() { return fileName; }
    public long getFileSize() { return fileSize; }
    public String getSender() { return sender; }
    public List<String> getRecipients() { return recipients; }
    public ServerSocket getUploadSocket() { return uploadSocket; }
    public String getTempFilePath() { return tempFilePath; }
    public ServerSocket getDownloadSocket() { return downloadSocket; }
    public void setTempFilePath(String path) { this.tempFilePath = path; }
    public void setDownloadSocket(ServerSocket socket) { this.downloadSocket = socket; }
    public void closeUploadSocket() { try { if (uploadSocket != null) uploadSocket.close(); } catch (IOException e) {e.printStackTrace();} }
    public void closeDownloadSocket() { try { if (downloadSocket != null) downloadSocket.close(); } catch (IOException e) { e.printStackTrace();} }
}
