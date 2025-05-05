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

    // For reconnection support
    public static ConcurrentHashMap<String, ClientHandler> sessionMap = new ConcurrentHashMap<>();
    private static final long SESSION_TIMEOUT = 5 * 60 * 1000; // 5 minutes in milliseconds

    public static void main(String[] args) {
        // Initialize the database and create the users table if it doesn't exist
        Database.createUsersTable();
        Database.createDocumentsTable();
        Database.createSharingTable();

        // Start the session monitor thread
        startSessionMonitorThread();

        // Start the server to listen for client connections
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server started. Waiting for clients to connect...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket.getInetAddress());

                DataInputStream tempIn = new DataInputStream(clientSocket.getInputStream());
                String initialCommand = tempIn.readUTF();

                ClientHandler clientHandler;

                // Check for reconnection attempts
                if ("reconnect".equals(initialCommand)) {
                    String sessionId = tempIn.readUTF();
                    clientHandler = new ClientHandler(clientSocket, sessionId);
                } else {
                    // Create normal client handler and inject the initial command
                    clientHandler = new ClientHandler(clientSocket);
                    // Pass the initial command to the handler
                    clientHandler.setInitialCommand(initialCommand);
                }

                new Thread(clientHandler).start();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void startSessionMonitorThread() {
        Thread monitor = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(60000); // Check every minute

                    long currentTime = System.currentTimeMillis();
                    sessionMap.forEach((id, handler) -> {
                        if (currentTime - handler.getLastActive() > SESSION_TIMEOUT) {
                            // Session expired, clean up resources
                            System.out.println("Session expired and cleaned up: " + id);
                            handler.cleanupResources();
                            sessionMap.remove(id);
                        }
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });

        monitor.setDaemon(true);
        monitor.start();
    }

    // Queue a message to all connected clients for a document except the sender
    public static void broadcastMessage(String documentName, ClientHandler sender, Message message) {
        CopyOnWriteArrayList<ClientHandler> handlers = activeEditors.getOrDefault(documentName,
                new CopyOnWriteArrayList<>());

        for (ClientHandler client : handlers) {
            if (client != sender) {
                if (client.isConnected()) {
                    try {
                        message.sendTo(client.getOutputStream());
                    } catch (IOException e) {
                        // Queue message for reconnection
                        client.queueMessage(message);
                    }
                } else {
                    // Client is disconnected but within reconnection window
                    client.queueMessage(message);
                }
            }
        }
    }
}
