package server;

import crdt.CRDTChar;
import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class CollabServer {

    public static final int PORT = 12345;
    public static ConcurrentHashMap<String, CopyOnWriteArrayList<ClientHandler>> activeEditors = new ConcurrentHashMap<>();
    public static Map<String, List<CRDTChar>> crdtStorage = new ConcurrentHashMap<String, List<CRDTChar>>();
    public static final Map<String, Map<Integer, String>> cursorColors = new HashMap<>();

    public static void main(String[] args) {
        try {
            System.out.println("🟢 Initializing database tables...");
            Database.createUsersTable();
            Database.createDocumentsTable();
            Database.createSharingTable();

            try (ServerSocket serverSocket = new ServerSocket(PORT)) {
                System.out.println("✅ Server started on port " + PORT + ". Waiting for clients...");

                while (true) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        System.out.println("🟡 New client connected: " + clientSocket.getInetAddress());

                        ClientHandler clientHandler = new ClientHandler(clientSocket);
                        new Thread(clientHandler).start();
                    } catch (IOException e) {
                        System.err.println("❌ Error while accepting client connection: " + e.getMessage());
                        e.printStackTrace();
                    } catch (Exception e) {
                        System.err.println("❗ Unexpected error during client connection handling:");
                        e.printStackTrace();
                    }
                }

            } catch (IOException e) {
                System.err.println("🚨 Failed to start server on port " + PORT + ": " + e.getMessage());
                e.printStackTrace();
            }

        } catch (Exception e) {
            System.err.println("🔥 Fatal server startup error:");
            e.printStackTrace();
        }
    }
}
