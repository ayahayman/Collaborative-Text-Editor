package client;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;

class LoginFrame extends JFrame {
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JButton loginButton, signupButton;

    public LoginFrame() {
        setTitle("Login");
        setSize(400, 300);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Set a background color for the window
        getContentPane().setBackground(new Color(255, 255, 255));
        setLayout(new BorderLayout());

        // Add a panel to hold the login form components
        JPanel panel = new JPanel();
        panel.setLayout(new GridLayout(4, 2, 10, 10));
        panel.setBackground(new Color(255, 255, 255));

        // Label for username and password
        JLabel usernameLabel = new JLabel("Username:");
        JLabel passwordLabel = new JLabel("Password:");
        usernameLabel.setHorizontalAlignment(SwingConstants.CENTER);
        passwordLabel.setHorizontalAlignment(SwingConstants.CENTER);

        // Initialize components
        usernameField = new JTextField(20);
        passwordField = new JPasswordField(20);
        loginButton = new JButton("Login");
        signupButton = new JButton("Sign Up");

        // Style the buttons
        loginButton.setBackground(new Color(34, 193, 195));
        loginButton.setForeground(Color.WHITE);
        signupButton.setBackground(new Color(253, 181, 28));
        signupButton.setForeground(Color.WHITE);

        // Add components to the panel
        panel.add(usernameLabel);
        panel.add(usernameField);
        panel.add(passwordLabel);
        panel.add(passwordField);
        panel.add(new JLabel()); // Empty space
        panel.add(new JLabel()); // Empty space
        panel.add(loginButton);
        panel.add(signupButton);

        // Center the form
        JPanel centerPanel = new JPanel();
        centerPanel.add(panel);
        centerPanel.setBackground(new Color(255, 255, 255));

        // Add the centered panel to the frame
        add(centerPanel, BorderLayout.CENTER);

        // Action listeners for buttons
        loginButton.addActionListener(e -> loginUser());
        signupButton.addActionListener(e -> openSignUpFrame());
    }

    private void loginUser() {
        String username = usernameField.getText();
        String password = new String(passwordField.getPassword());

        // Send login details to the server for validation
        try (Socket socket = new Socket("localhost", 12345);
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                DataInputStream in = new DataInputStream(socket.getInputStream())) {

            out.writeUTF("login"); // Send 'login' request
            out.writeUTF(username); // Send username
            out.writeUTF(password); // Send plain password (no hashing needed)

            String response = in.readUTF();
            if ("Login successful".equals(response)) {
                JOptionPane.showMessageDialog(this, "Login successful!");
                // Proceed to the editor screen or next part of the application
            } else {
                JOptionPane.showMessageDialog(this, "Invalid credentials", "Error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void openSignUpFrame() {
        new SignUpFrame().setVisible(true);
        this.dispose(); // Close the login screen
    }
}