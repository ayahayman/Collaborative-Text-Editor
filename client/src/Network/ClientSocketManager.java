// client/src/network/ClientSocketManager.java
package network;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClientSocketManager {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    public void connect(String host, int port) throws IOException {
        this.socket = new Socket(host, port);
        this.out = new PrintWriter(socket.getOutputStream(), true);
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    }

    public String sendAuthRequest(String request) throws IOException {
        out.println(request);
        return in.readLine();
    }

    public void disconnect() throws IOException {
        if (socket != null) {
            socket.close();
        }
    }

    // Add other methods for document collaboration
}