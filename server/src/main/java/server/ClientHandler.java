package server;

import org.mindrot.jbcrypt.BCrypt;

import java.io.*;
import java.net.*;

public class ClientHandler extends Thread {
    private Socket clientSocket;
    private DataInputStream in;
    private DataOutputStream out;

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
        try {
            this.in = new DataInputStream(socket.getInputStream());
            this.out = new DataOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void run() {
        try {
            // Read the request type (login or signup)
            String requestType = in.readUTF();
            if ("login".equals(requestType)) {
                handleLogin();
            } else if ("signup".equals(requestType)) {
                handleSignup();
            }
            clientSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleLogin() throws IOException {
        System.out.println("Handling login request");
        String username = in.readUTF();
        String password = in.readUTF();
        System.out.println("Username: " + username + ", Password: " + password);
        boolean isValid = validateUser(username, password);
        if (isValid) {
            out.writeUTF("Login successful");
            System.out.println("User " + username + " logged in successfully.");
        } else {
            out.writeUTF("Invalid credentials");
            System.out.println("Invalid login attempt for user " + username);
        }
    }

    private void handleSignup() throws IOException {
        String username = in.readUTF();
        String password = in.readUTF();

        if (Database.userExists(username)) {
            out.writeUTF("Username already exists");
        } else {
            String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());
            Database.addUser(username, hashedPassword);
            out.writeUTF("Signup successful");
        }
    }

    private boolean validateUser(String username, String password) {
        return Database.validateUser(username, password);
    }
}
