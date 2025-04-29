package client;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;

class SignUpFrame extends JFrame {
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JButton signUpButton;

    public SignUpFrame() {
        setTitle("Sign Up");
        setSize(400, 300);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Set a background color for the window
        getContentPane().setBackground(new Color(255, 255, 255));
        setLayout(new BorderLayout());

        // Add a panel to hold the sign-up form components
        JPanel panel = new JPanel();
        panel.setLayout(new GridLayout(4, 2, 10, 0));
        panel.setBackground(new Color(255, 255, 255));

        // Label for username and password
        JLabel usernameLabel = new JLabel("Username:");
        JLabel passwordLabel = new JLabel("Password:");
        usernameLabel.setHorizontalAlignment(SwingConstants.CENTER);
        passwordLabel.setHorizontalAlignment(SwingConstants.CENTER);

        // Initialize components
        usernameField = new JTextField(20);
        passwordField = new JPasswordField(20);
        signUpButton = new JButton("Sign Up");

        // Style the buttons
        signUpButton.setBackground(new Color(253, 181, 28));
        signUpButton.setForeground(Color.WHITE);

                d components to the panel
                .add(usernameLabel);
        panel.add(usernameField);
        panel.add(passwordLabel);
        panel.add(passwordField);
        panel.add(new JLabel()); // Empty space
        panel.add(new JLabel()); // Empty space
        panel.add(signUpButton);

        // Center the form
        JPanel centerPanel = ne JPanel();
        centerPanel.add(panel); 
        centerPanel.setBackground(new Color(255, 255, 255));

        // Add the centered panel to the frame
        add(centerPanel, BorderLayout.CENTER);

        // Sign-up button action
        signUpButton.addActionListener(e -> signUpUser());
    }

    private void signUpUser() {
        String username = usernameField.getText();
        String password = new String(passwordField.getPassword());

        // Send sign-up details to the server to add the new user
        try (Socket socket = new Socket("localhost", 12345);
             DataOutputStream out = new DataOutputStream(socket.getOutputStream());
             DataInputStream in = new DataInputStream(socket.getInputStream())) {

            out.writeUTF("signup"); // Send 'signup' request
            out.writeUTF(username);  // Send username
            out.writeUTF(password);  // Send plain password (no hashing needed)

            String response = in.readUTF();
            if ("Signup successful".equals(response)) {
                JOptionPane.showMessageDialog(this, "Sign-up successful!");
                this.dispose();  // Close the sign-up screen after success
                new LoginFrame().setVisible(true);  // Show the login screen again
            } else {
                JOptionPane.showMessageDialog(this, "Username already exists", "Error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}