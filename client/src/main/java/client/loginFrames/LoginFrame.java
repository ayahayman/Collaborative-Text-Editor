package client.loginFrames;

import javax.swing.*;

import client.ClientApp;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.net.*;

public class LoginFrame extends JFrame {
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JButton loginButton, signUpButton;

    public LoginFrame() {
        setTitle("Login");
        setSize(500, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        GradientPanel background = new GradientPanel();
        background.setLayout(new GridBagLayout());
        setContentPane(background);

        JPanel formPanel = new JPanel();
        formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));
        formPanel.setBackground(new Color(255, 255, 255, 220));
        formPanel.setBorder(BorderFactory.createEmptyBorder(30, 40, 30, 40));

        JLabel loginLabel = new JLabel("LOGIN");
        loginLabel.setFont(new Font("Arial", Font.BOLD, 24));
        loginLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        loginLabel.setForeground(new Color(0, 51, 102));

        usernameField = new JTextField(20);
        passwordField = new JPasswordField(20);
        styleInputField(usernameField, "Username");
        styleInputField(passwordField, "Password");

        loginButton = new JButton("LOGIN");
        signUpButton = new JButton("SIGN UP");

        styleButton(loginButton, new Color(34, 193, 195));
        styleButton(signUpButton, new Color(253, 181, 28));

        formPanel.add(loginLabel);
        formPanel.add(Box.createVerticalStrut(20));
        formPanel.add(usernameField);
        formPanel.add(Box.createVerticalStrut(15));
        formPanel.add(passwordField);
        formPanel.add(Box.createVerticalStrut(20));
        formPanel.add(loginButton);
        formPanel.add(Box.createVerticalStrut(10));
        formPanel.add(signUpButton);

        background.add(formPanel);

        loginButton.addActionListener(e -> loginUser());
        signUpButton.addActionListener(e -> openSignUpFrame());
    }

    private void styleInputField(JTextField field, String placeholder) {
        field.setMaximumSize(new Dimension(300, 40));
        field.setFont(new Font("Arial", Font.PLAIN, 16));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(5, 10, 5, 10)));
        field.setText(placeholder);
        field.setForeground(Color.GRAY);
        field.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                if (field.getText().equals(placeholder)) {
                    field.setText("");
                    field.setForeground(Color.BLACK);
                }
            }

            public void focusLost(java.awt.event.FocusEvent evt) {
                if (field.getText().isEmpty()) {
                    field.setForeground(Color.GRAY);
                    field.setText(placeholder);
                }
            }
        });
    }

    private void styleButton(JButton button, Color color) {
        button.setBackground(color);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setFont(new Font("Arial", Font.BOLD, 14));
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        button.setMaximumSize(new Dimension(300, 40));
        button.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));

        // Hover effect
        button.addMouseListener(new MouseAdapter() {
            Color original = color;

            public void mouseEntered(MouseEvent e) {
                button.setBackground(original.brighter());
            }

            public void mouseExited(MouseEvent e) {
                button.setBackground(original);
            }
        });
    }

    private void loginUser() {
        String username = usernameField.getText();
        String password = new String(passwordField.getPassword());

        try (Socket socket = new Socket("localhost", 12345);
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                DataInputStream in = new DataInputStream(socket.getInputStream())) {

            out.writeUTF("login"); // Send login request
            out.writeUTF(username); // Send username
            out.writeUTF(password); // Send password

            String response = in.readUTF();
            if ("Login successful".equals(response)) {
                // Read the user ID from the server
                int userId = in.readInt(); // Expect the server to send the user ID after success
                JOptionPane.showMessageDialog(this, "Login successful!");

                // Open the Documents frame and pass the user ID
                ClientApp.openDocumentsFrame(userId);

                // Close the login screen
                this.dispose();
            } else {
                JOptionPane.showMessageDialog(this, "Invalid credentials", "Error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void openSignUpFrame() {
        new SignUpFrame().setVisible(true);
        this.dispose();
    }
}