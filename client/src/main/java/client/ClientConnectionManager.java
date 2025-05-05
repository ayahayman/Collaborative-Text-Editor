package client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class ClientConnectionManager {
    private static Socket socket;
    private static DataOutputStream out;
    private static DataInputStream in;
    private static String siteId; // 🆕 client identifier for CRDT operations


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
    // 🆕 For CRDT usage
    public static void setSiteId(String id) {
        siteId = id;
    }

    public static String getSiteId() {
        return siteId;
    }
}
