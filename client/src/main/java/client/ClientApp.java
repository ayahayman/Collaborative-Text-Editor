package client;

import javax.swing.SwingUtilities;

import client.documentFrames.DocumentsFrame;
import client.loginFrames.LoginFrame;

public class ClientApp {
    public static final String SERVER_HOST = "localhost";; // Server host address

    public static void main(String[] args) {
        // Launch the login screen when the application starts
        SwingUtilities.invokeLater(() -> {
            new LoginFrame(SERVER_HOST).setVisible(true);
        });
    }

    public static void openDocumentsFrame(int userId) {
        SwingUtilities.invokeLater(() -> {
            new DocumentsFrame(userId, SERVER_HOST,12345).setVisible(true);
        });
    }
}
