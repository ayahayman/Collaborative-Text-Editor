package server;

import crdt.CRDTChar;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class CollabServer {
    public static final int PORT = 12345;
    
    // Active client sessions
    public static final ConcurrentHashMap<String, CopyOnWriteArrayList<ClientHandler>> activeEditors = 
        new ConcurrentHashMap<>();
    
    // Disconnected clients that may reconnect (key: documentName)
    public static final ConcurrentHashMap<String, CopyOnWriteArrayList<ClientHandler>> disconnectedClients = 
        new ConcurrentHashMap<>();
    
    // CRDT storage (key: documentName)
    public static Map<String, List<CRDTChar>> crdtStorage = new ConcurrentHashMap<String, List<CRDTChar>>();
    
    // Cursor colors (key: documentName, value: map of userId to color)
    public static final ConcurrentHashMap<String, ConcurrentHashMap<Integer, String>> cursorColors = 
        new ConcurrentHashMap<>();

    public static void main(String[] args) {
        configureUncaughtExceptionHandler();
        initializeDatabase();
        startServer();
    }

    private static void configureUncaughtExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            System.err.println("💥 Uncaught exception in thread " + thread.getName());
            throwable.printStackTrace();
        });
    }

    private static void initializeDatabase() {
        Database.createUsersTable();
        Database.createDocumentsTable();
        Database.createSharingTable();
        
        // Start periodic cleanup of disconnected clients
        startDisconnectedClientCleanup();
    }

    private static void startDisconnectedClientCleanup() {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            disconnectedClients.forEach((docName, clients) -> {
                clients.removeIf(client -> 
                    now - client.getLastHeartbeat() > 300000); // 5 minutes
                
                // Remove empty document entries
                if (clients.isEmpty()) {
                    disconnectedClients.remove(docName);
                }
            });
        }, 1, 1, TimeUnit.MINUTES); // Run every minute
    }

    private static void startServer() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("🚀 Server started on port " + PORT);
            acceptClientConnections(serverSocket);
        } catch (IOException e) {
            System.err.println("❌ Server socket error:");
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void acceptClientConnections(ServerSocket serverSocket) {
        while (true) {
            try {
                Socket clientSocket = serverSocket.accept();
                handleNewClient(clientSocket);
            } catch (IOException e) {
                System.err.println("⚠️ Error accepting client connection:");
                e.printStackTrace();
            }
        }
    }

    private static void handleNewClient(Socket clientSocket) {
        try {
            System.out.println("🔗 New client connected: " + 
                clientSocket.getInetAddress() + ":" + clientSocket.getPort());
                
            ClientHandler clientHandler = new ClientHandler(clientSocket);
            clientHandler.start();
        } catch (Exception e) {
            System.err.println("⚠️ Error creating client handler:");
            e.printStackTrace();
            try {
                clientSocket.close();
            } catch (IOException ioException) {
                System.err.println("⚠️ Error closing failed client socket:");
                ioException.printStackTrace();
            }
        }
    }
}