package client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class ClientConnectionManager {
    private static Socket socket;
    private static DataOutputStream out;
    private static DataInputStream in;

    public static void connect(String host, int port) throws IOException {
        if (socket == null || socket.isClosed()) {
            socket = new Socket(host, port);
            out = new DataOutputStream(socket.getOutputStream());
            in = new DataInputStream(socket.getInputStream());
        }
    }

    public static DataOutputStream getOut() {
        return out;
    }

    public static DataInputStream getIn() {
        return in;
    }

    public static void close() throws IOException {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }
}
