package server;

import java.io.*;
import java.net.*;

public class CollabServer {
    public static final int PORT = 12345;

    public static void main(String[] args) {
        // Initialize the database and create the users table if it doesn't exist
        Database.createUsersTable();
        Database.createDocumentsTable();
        Database.createSharingTable();

        // Start the server to listen for client connections
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server started. Waiting for clients to connect...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket.getInetAddress());

                ClientHandler clientHandler = new ClientHandler(clientSocket);
                new Thread(clientHandler).start();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}