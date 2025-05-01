package client;

import javax.swing.*;

import client.documentFrames.DocumentsFrame;
import client.loginFrames.LoginFrame;

public class ClientApp {
    public static void main(String[] args) {
        // Launch the login screen when the application starts
        SwingUtilities.invokeLater(() -> {
            new LoginFrame().setVisible(true);
        });
    }

    public static void openDocumentsFrame(int userId) {
        SwingUtilities.invokeLater(() -> {
            new DocumentsFrame(userId).setVisible(true);
        });
    }
}
