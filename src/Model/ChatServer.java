package Model;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServer {
    private static final int PORT = 8080;
    public static final String LISTUSERS = "/listUsers";
    public static final String PRIVATEMESSAGES = "/tell";
    public static final String HELP = "/help";
    public static final String GROUP = "/group";
    public static final String EXIT = "/exit";
    public static final String SENDFILE = "/sendfile";
    public static final String GETFILE = "/getfile";

    // A lista de clientes é mantida como PRIVATE para segurança e encapsulamento.
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

    public static synchronized void privateMessage(String targetUser, String message, ClientHandler sender, boolean groupMessage) {
        ClientHandler target = getClientByName(targetUser); // Usando o método seguro
        String targetPrefixo = groupMessage ? "Mensagem em Grupo de " : "Privado de ";
        String senderPrefixo = groupMessage ? "Mensagem em Grupo para " : "Privado para ";
        if (target != null) {
            target.sendMessage("[" + targetPrefixo + sender.getUserName() + "]: " + message);
            sender.sendMessage("[" + senderPrefixo + targetUser + "]: " + message);
        } else {
            sender.sendMessage("Usuário '" + targetUser + "' não encontrado.");
        }
    }

    public static synchronized void WarningMessage(String targetUser, String type, ClientHandler sender, List<String> group) {
        ClientHandler target = getClientByName(targetUser); // Usando o método seguro
        if (target != null) {
            target.sendMessage("======|" + sender.getUserName() + " adicionou voce em um grupo |======");
            target.sendMessage(sender.getUserName() + " " + group.toString());
        }
    }

    public static synchronized void addClient(String userName, ClientHandler client) {
        clients.put(userName, client);
        broadcast(userName + " entrou no chat.", client);
    }

    public static synchronized void removeClient(String userName, ClientHandler client) {
        clients.remove(userName);
        broadcast(userName + " saiu do chat.", client);
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
            out.println(ChatServer.PRIVATEMESSAGES + " destinatario Mensagem -> Enviar mensagem privada");
            out.println(ChatServer.GROUP + " user1 user2 ... -> Criar um grupo de chat [MAX 5]");
            out.println("======| Comandos de Grupo |======");
            out.println(ChatServer.SENDFILE + " <caminho_completo_do_arquivo> -> Enviar um arquivo para o grupo");
            out.println(ChatServer.GETFILE + " <ID_do_arquivo> -> Baixar um arquivo recebido");
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                do {
                    setUserName(in, out);
                    // Usando o método "porteiro" para verificar se o usuário existe
                    if (ChatServer.isUserConnected(userName)) {
                        out.println("Nome do usuário indisponivel.");
                    }
                    if (userName == null || userName.trim().isEmpty()) {
                        out.println("Nome Invalido.");
                        userName = ""; // Força o loop a continuar
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
                            } else {
                                out.println("Você não pode enviar uma mensagem privada para si mesmo.");
                            }
                        } else {
                            sendMessage("Formato inválido. Use: /tell <destinatario> <mensagem>");
                        }
                    } else if (message.toLowerCase().startsWith(ChatServer.GROUP)) {
                        String[] parts = message.split(" ");
                        if (parts.length < 2) {
                            out.println("Formato inválido. Especifique pelo menos um destinatário.");
                            continue;
                        }
                        if (parts.length > 6) { // 1 para /group + 5 usuários
                            out.println("Limite de 5 usuários por grupo excedido.");
                            continue;
                        }

                        List<String> groupMembers = new ArrayList<>();
                        for (int i = 1; i < parts.length; i++) {
                            String targetUser = parts[i];
                            if (!targetUser.equalsIgnoreCase(userName) && ChatServer.isUserConnected(targetUser)) {
                                groupMembers.add(targetUser);
                            } else if (!ChatServer.isUserConnected(targetUser)) {
                                out.println("Aviso: Usuário '" + targetUser + "' não encontrado.");
                            }
                        }

                        if (groupMembers.isEmpty()) {
                            out.println("Nenhum usuário válido foi adicionado ao grupo.");
                        } else {
                            for (String member : groupMembers) {
                                ChatServer.WarningMessage(member, "WarningGroup", this, groupMembers);
                            }
                            out.println("=================================================");
                            out.println("======| Você está em um grupo. Digite /exit para sair. |======");
                            out.println("Você está em um grupo com: " + groupMembers);

                            boolean inGroup = true;
                            while (inGroup) {
                                message = in.readLine();
                                if (message == null || message.equalsIgnoreCase(ChatServer.EXIT)) {
                                    out.println("============| Você saiu do grupo |============");
                                    inGroup = false;
                                } else if (message.toLowerCase().startsWith(ChatServer.SENDFILE)) {
                                    String[] fileParts = message.split(" ", 2);
                                    if (fileParts.length == 2) {
                                        File file = new File(fileParts[1]);
                                        if (file.exists() && !file.isDirectory()) {
                                            fileTransferManager.requestUpload(this, groupMembers, file);
                                        } else {
                                            out.println("ERRO: Arquivo não encontrado ou é um diretório.");
                                        }
                                    } else {
                                        out.println("Uso inválido. Use: /sendfile <caminho_completo_do_arquivo>");
                                    }
                                } else if (message.toLowerCase().startsWith(ChatServer.GETFILE)) {
                                    String[] getParts = message.split(" ", 2);
                                    if (getParts.length == 2) fileTransferManager.requestDownload(this, getParts[1]);
                                    else out.println("Uso inválido. Use: /getfile <ID_do_arquivo>");
                                } else {
                                    for (String targetUser : groupMembers) {
                                        ChatServer.privateMessage(targetUser, message, this, true);
                                    }
                                }
                            }
                        }
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

// --- CLASSES AUXILIARES (FORA DA CLASSE ChatServer) ---

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
                downloader.sendMessage("ERRO: Não foi possível iniciar o envio do arquivo.");
                e.printStackTrace();
            }
        } else {
            downloader.sendMessage("ERRO: ID de arquivo inválido ou o arquivo não está mais disponível.");
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
    private String fileId, fileName, sender, tempFilePath;
    private long fileSize;
    private List<String> recipients;
    private ServerSocket uploadSocket;
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
    public void closeUploadSocket() { try { if (uploadSocket != null) uploadSocket.close(); } catch (IOException e) {} }
    public void closeDownloadSocket() { try { if (downloadSocket != null) downloadSocket.close(); } catch (IOException e) {} }
}