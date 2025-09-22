package Model;

import java.io.*;
import java.net.*;

public class ChatClient {
    //Mude para ajustar o servidorS
    private static final String SERVER_IP = "127.0.0.1";
    //private static final String SERVER_IP = "IP_DO_SERVIDOR, PRECISA SER A MAQUINA HOSTEANDO";
    private static final int SERVER_PORT = 8080;
    private static final String DOWNLOAD_DIR = "client_downloads";
    private static String pendingFileUploadPath = null;

    public static void main(String[] args) {
        File downloadDir = new File(DOWNLOAD_DIR);
        if (!downloadDir.exists()) {
            downloadDir.mkdirs();
        }

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
                        if (response.startsWith("UPLOAD_READY|")) {
                            String[] parts = response.split("\\|", 3);
                            String fileId = parts[1];
                            int port = Integer.parseInt(parts[2]);
                            if (pendingFileUploadPath != null) {
                                // Inicia o upload em uma nova thread
                                startUpload(SERVER_IP, port, pendingFileUploadPath);
                                pendingFileUploadPath = null; // Limpa o caminho após iniciar
                            }
                        } else if (response.startsWith("FILE_NOTIFICATION|")) {
                            String[] parts = response.split("\\|", 4);
                            String sender = parts[1];
                            String fileName = parts[2];
                            String fileId = parts[3];
                            System.out.println("\n>>> " + sender + " enviou o arquivo '" + fileName + "'");
                            System.out.println(">>> Para baixar, digite: /getfile " + fileId);
                        } else if (response.startsWith("DOWNLOAD_READY|")) {
                            String[] parts = response.split("\\|", 4);
                            String fileName = parts[1];
                            long fileSize = Long.parseLong(parts[2]);
                            int port = Integer.parseInt(parts[3]);
                            // Inicia o download em uma nova thread
                            startDownload(SERVER_IP, port, fileName, fileSize);
                        }
                        else {
                            System.out.println(response);
                        }
                    }
                } catch (IOException e) {
                    System.out.println("Conexão com o servidor perdida.");
                }
            }).start();

            // Enviar mensagens digitadas sempre que apertar "ENTER"
            String text;
            while ((text = keyboard.readLine()) != null) {
                if (text.toLowerCase().startsWith("/sendfileto")) {
                    String[] parts = text.split(" ", 3);
                    if (parts.length == 3) {
                        String targetUser = parts[1];
                        String filePath = parts[2];

                        if (filePath.startsWith("\"") && filePath.endsWith("\"")) {
                            filePath = filePath.substring(1, filePath.length() - 1);
                        }

                        File file = new File(filePath);
                        if (file.exists() && !file.isDirectory()) {
                            pendingFileUploadPath = filePath;
                            out.println(text); // Envia o comando completo para o servidor
                        } else {
                            System.out.println("ERRO: Arquivo não encontrado ou inválido.");
                        }
                    } else {
                        System.out.println("Uso inválido. Use: /sendfileto <usuario> <caminho_completo_do_arquivo>");
                    }
                }
                if (text.toLowerCase().startsWith("/sendfile ")) {
                    String[] parts = text.split(" ", 2);
                    if (parts.length == 2) {
                        String filePath = parts[1];

                        // Quando o usuário digita /sendfile, a thread de upload real será iniciada pela resposta do servidor.
                        // Aqui apenas validamos e enviamos a mensagem
                        if (filePath.startsWith("\"") && filePath.endsWith("\"")) {
                            filePath = filePath.substring(1, filePath.length() - 1);
                        }
                        File file = new File(filePath);
                        if(file.exists() && !file.isDirectory()){
                            pendingFileUploadPath = filePath;
                            out.println(text); // Envia o comando para o servidor
                        } else {
                            System.out.println("ERRO: Arquivo não encontrado ou inválido.");
                        }
                    } else {
                        out.println(text);
                    }
                } else {
                    out.println(text);
                }

            }
            //Tratativa para informar que o servidor caiu.
        } catch (ConnectException e){
            System.out.println("\nO servidor se encontra desligado ou sua porta está incorreta;\nColoque uma porta disponivel na maquina do servidor para se conectar");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void startUpload(String host, int port, String filePath) {
        new Thread(() -> {
            File file = new File(filePath);
            System.out.println("Iniciando upload para " + host + ":" + port + "...");
            try (Socket fileSocket = new Socket(host, port);
                 FileInputStream fis = new FileInputStream(file);
                 DataOutputStream dos = new DataOutputStream(fileSocket.getOutputStream())) {

                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    dos.write(buffer, 0, bytesRead);
                }
                dos.flush();
                System.out.println("Upload do arquivo '" + file.getName() + "' concluído com sucesso!");

            } catch (IOException e) {
                System.out.println("ERRO durante o upload: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    private static void startDownload(String host, int port, String fileName, long fileSize) {
        new Thread(() -> {
            try (Socket fileSocket = new Socket(host, port);
                 DataInputStream dis = new DataInputStream(fileSocket.getInputStream())) {

                System.out.println("Conectado para baixar o arquivo: " + fileName);
                File file = new File(DOWNLOAD_DIR, fileName);

                try (FileOutputStream fos = new FileOutputStream(file)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    long totalRead = 0;

                    while (totalRead < fileSize && (bytesRead = dis.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                        totalRead += bytesRead;
                    }
                }
                System.out.println("Download do arquivo '" + fileName + "' concluído com sucesso! Salvo em '" + DOWNLOAD_DIR + "'.");

            } catch (IOException e) {
                System.out.println("Erro durante o download: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }
}
