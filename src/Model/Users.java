package Model;

import java.net.Socket;

public class Users {
    private String nome;
    private String ip;
    private int port;
    private Socket socket;

    public Users(String nome, Socket socket) {
        this.nome = nome;
        this.socket = socket;
        this.ip = socket.getInetAddress().getHostAddress();
        this.port = socket.getPort();
    }

    public String getNome() {
        return nome;
    }

    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }

    public Socket getSocket() {
        return socket;
    }

    @Override
    public String toString() {
        return "nome: " + nome + "\n"
                + "ip: " + ip + "\n"
                + "port: " + port + "\n";
    }
}
