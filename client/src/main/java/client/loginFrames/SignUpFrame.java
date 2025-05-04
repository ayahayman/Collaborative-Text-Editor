package client.loginFrames;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

public class SignUpFrame extends JFrame {
    private static String SERVER_HOST;
    private static int PORT;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JButton signUpButton, backButton;

    public SignUpFrame(String serverHost,int port) {
        SERVER_HOST = serverHost;
        PORT=port;
        setTitle("Sign Up");
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

        JLabel signUpLabel = new JLabel("SIGN UP");
        signUpLabel.setFont(new Font("Arial", Font.BOLD, 24));
        signUpLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        signUpLabel.setForeground(new Color(0, 51, 102));

        usernameField = new JTextField(20);
        passwordField = new JPasswordField(20);
        styleInputField(usernameField, "Username");
        styleInputField(passwordField, "Password");

        signUpButton = new JButton("CREATE ACCOUNT");
        backButton = new JButton("BACK TO LOGIN");

        styleButton(signUpButton, new Color(253, 181, 28));
        styleButton(backButton, new Color(34, 193, 195));

        formPanel.add(signUpLabel);
        formPanel.add(Box.createVerticalStrut(20));
        formPanel.add(usernameField);
        formPanel.add(Box.createVerticalStrut(15));
        formPanel.add(passwordField);
        formPanel.add(Box.createVerticalStrut(20));
        formPanel.add(signUpButton);
        formPanel.add(Box.createVerticalStrut(10));
        formPanel.add(backButton);

        background.add(formPanel);

        signUpButton.addActionListener(e -> signUpUser());
        backButton.addActionListener(e -> {
            new LoginFrame(SERVER_HOST,PORT).setVisible(true);
            this.dispose();
        });
    }

    private void styleInputField(JTextField field, String placeholder) {
        field.setMaximumSize(new Dimension(300, 40));
        field.setFont(new Font("Arial", Font.PLAIN, 16));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(5, 10, 5, 10)));
        field.setText(placeholder);
        field.setForeground(Color.GRAY);
        field.addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) {
                if (field.getText().equals(placeholder)) {
                    field.setText("");
                    field.setForeground(Color.BLACK);
                }
            }

            public void focusLost(FocusEvent e) {
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

    private void signUpUser() {
        String username = usernameField.getText();
        String password = new String(passwordField.getPassword());

        try (Socket socket = new Socket(SERVER_HOST, PORT);
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                DataInputStream in = new DataInputStream(socket.getInputStream())) {

            out.writeUTF("signup");
            out.writeUTF(username);
            out.writeUTF(password);

            String response = in.readUTF();
            if ("Signup successful".equals(response)) {
                JOptionPane.showMessageDialog(this, "Sign-up successful!");
                new LoginFrame(SERVER_HOST,PORT).setVisible(true);
                this.dispose();
            } else {
                JOptionPane.showMessageDialog(this, "Username already exists", "Error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
