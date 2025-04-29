// client/src/ui/controllers/LoginController.java
package ui.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import network.AuthService;
import network.ClientSocketManager;

public class LoginController {
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;
    
    private AuthService authService;
    
    public void initialize() {
        try {
            ClientSocketManager socketManager = new ClientSocketManager();
            socketManager.connect("localhost", 8080);
            this.authService = new AuthService(socketManager);
        } catch (IOException e) {
            errorLabel.setText("Connection error: " + e.getMessage());
        }
    }
    
    @FXML
    private void handleLogin() {
        try {
            if (authService.login(usernameField.getText(), passwordField.getText())) {
                // Switch to editor view
            } else {
                errorLabel.setText("Invalid credentials");
            }
        } catch (IOException e) {
            errorLabel.setText("Network error: " + e.getMessage());
        }
    }
}