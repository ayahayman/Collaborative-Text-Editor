package client;

import javax.swing.*;

import client.documentFrames.DocumentsFrame;
import client.loginFrames.LoginFrame;

public class ClientApp {
    public static final String SERVER_HOST = "192.168.100.249"; // Server host address

    public static void main(String[] args) {
        // Launch the login screen when the application starts
        SwingUtilities.invokeLater(() -> {
            new LoginFrame(SERVER_HOST).setVisible(true);
        });
    }

    public static void openDocumentsFrame(int userId) {
        SwingUtilities.invokeLater(() -> {
            new DocumentsFrame(userId, SERVER_HOST).setVisible(true);
        });
    }
}
