// server/src/server/CollabServer.java
package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import db.Database;

public class CollabServer {
    private static final int PORT = 8080;
    private final ExecutorService pool = Executors.newFixedThreadPool(10);
    private final Database database;

    public CollabServer(Database database) {
        this.database = database;
    }

    public void start() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server started on port " + PORT);
            
            while (true) {
                Socket clientSocket = serverSocket.accept();
                pool.execute(new ClientHandler(clientSocket, database));
            }
        }
    }

    public static void main(String[] args) {
        try {
            Database db = new Database();
            CollabServer server = new CollabServer(db);
            server.start();
        } catch (Exception e) {
            System.err.println("Server error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}