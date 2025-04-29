// server/src/server/ClientHandler.java
package server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import db.Database;
import db.Database.UserData;

public class ClientHandler implements Runnable {
    private final Socket clientSocket;
    private final Database database;

    public ClientHandler(Socket socket, Database database) {
        this.clientSocket = socket;
        this.database = database;
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {
            
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                String[] parts = inputLine.split(":");
                String command = parts[0];
                String username = parts[1];
                String password = parts[2];

                switch (command) {
                    case "LOGIN":
                        handleLogin(username, password, out);
                        break;
                    case "REGISTER":
                        handleRegister(username, password, out);
                        break;
                    default:
                        out.println("INVALID_COMMAND");
                }
            }
        } catch (IOException e) {
            System.err.println("Client handler error: " + e.getMessage());
        }
    }

    private void handleLogin(String username, String password, PrintWriter out) {
        try {
            UserData userData = database.getUser(username);
            if (userData != null && verifyPassword(password, userData.salt, userData.hashedPassword)) {
                out.println("SUCCESS");
            } else {
                out.println("FAILURE");
            }
        } catch (SQLException e) {
            out.println("ERROR");
            System.err.println("Database error during login: " + e.getMessage());
        }
    }

    private void handleRegister(String username, String password, PrintWriter out) {
        try {
            String salt = generateSalt();
            String hashedPassword = hashPassword(password, salt);
            
            if (database.createUser(username, salt, hashedPassword)) {
                out.println("SUCCESS");
            } else {
                out.println("FAILURE");
            }
        } catch (SQLException | NoSuchAlgorithmException e) {
            out.println("ERROR");
            System.err.println("Database error during registration: " + e.getMessage());
        }
    }

    private String generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    private String hashPassword(String password, String salt) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(Base64.getDecoder().decode(salt));
        byte[] hashedPassword = md.digest(password.getBytes());
        return Base64.getEncoder().encodeToString(hashedPassword);
    }

    private boolean verifyPassword(String password, String salt, String hashedPassword) {
        try {
            String testHash = hashPassword(password, salt);
            return hashedPassword.equals(testHash);
        } catch (NoSuchAlgorithmException e) {
            return false;
        }
    }
}