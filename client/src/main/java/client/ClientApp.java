package client;

import javax.swing.SwingUtilities;

import client.documentFrames.DocumentsFrame;
import client.loginFrames.LoginFrame;

public class ClientApp {
    public static final String SERVER_HOST = "192.168.1.50";; // Server host address
    public static final int PORT = 12345;
    public static void main(String[] args) {
        // Launch the login screen when the application starts
        SwingUtilities.invokeLater(() -> {
            new LoginFrame(SERVER_HOST,PORT).setVisible(true);
        });
    }

    public static void openDocumentsFrame(int userId) {
        SwingUtilities.invokeLater(() -> {
            new DocumentsFrame(userId, SERVER_HOST,PORT).setVisible(true);
        });
    }
}
