package client.documentFrames;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.UIManager;

import client.loginFrames.GradientPanel;

public class DocumentsFrame extends JFrame {
    private static String SERVER_HOST;
    private JPanel documentGrid;
    private JButton newDocumentButton;
    private JButton joinDocumentButton;
    private int userId;

    private static class DocumentEntry {
        String docName;
        String code;
        String role;

        DocumentEntry(String docName, String code, String role) {
            this.docName = docName;
            this.code = code;
            this.role = role;
        }
    }

    public DocumentsFrame(int userId, String serverHost) {
        this.userId = userId;
        SERVER_HOST = serverHost;
        setTitle("My Documents");
        setSize(900, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        GradientPanel background = new GradientPanel();
        background.setLayout(new BorderLayout());
        setContentPane(background);

        JLabel titleLabel = new JLabel("MY DOCUMENTS");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 28));
        titleLabel.setForeground(new Color(0, 51, 102));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        background.add(titleLabel, BorderLayout.NORTH);

        documentGrid = new JPanel();
        documentGrid.setLayout(new FlowLayout(FlowLayout.LEFT, 20, 20));
        documentGrid.setBackground(Color.WHITE);

        JScrollPane scrollPane = new JScrollPane(documentGrid);
        background.add(scrollPane, BorderLayout.CENTER);

        JPanel footerPanel = new JPanel();
        footerPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 20, 10));
        footerPanel.setOpaque(false);

        newDocumentButton = new JButton("Create New Document");
        joinDocumentButton = new JButton("Join Document");
        styleButton(newDocumentButton, new Color(34, 193, 195));
        styleButton(joinDocumentButton, new Color(253, 181, 28));

        footerPanel.add(newDocumentButton);
        footerPanel.add(joinDocumentButton);
        background.add(footerPanel, BorderLayout.SOUTH);

        newDocumentButton.addActionListener(e -> createNewDocument());
        joinDocumentButton.addActionListener(e -> joinDocumentWithCode());

        fetchDocuments();
    }

    private void styleButton(JButton button, Color color) {
        button.setBackground(color);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setFont(new Font("Arial", Font.BOLD, 14));
        button.setPreferredSize(new Dimension(180, 40));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setBorder(BorderFactory.createEmptyBorder(5, 15, 5, 15));
    }

    private void fetchDocuments() {
        try (Socket socket = new Socket(SERVER_HOST, 43013);
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                DataInputStream in = new DataInputStream(socket.getInputStream())) {

            out.writeUTF("getDocuments");
            out.writeInt(userId);
            int count = in.readInt();

            documentGrid.removeAll();

            for (int i = 0; i < count; i++) {
                String docName = in.readUTF();
                String code = in.readUTF();
                String role = in.readUTF();
                DocumentEntry entry = new DocumentEntry(docName, code, role);
                documentGrid.add(createDocumentCard(entry));

            }

            documentGrid.revalidate();
            documentGrid.repaint();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private JPanel createDocumentCard(DocumentEntry entry) {
        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
            }
        };

        card.setPreferredSize(new Dimension(160, 180));
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 220, 220), 1),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));
        card.setBackground(new Color(245, 245, 245));

        JLabel iconLabel;
        try {
            ImageIcon icon = new ImageIcon(getClass().getResource("src/assets/file_flat.png"));
            Image scaled = icon.getImage().getScaledInstance(64, 64, Image.SCALE_SMOOTH);
            iconLabel = new JLabel(new ImageIcon(scaled));
        } catch (Exception e) {
            iconLabel = new JLabel(UIManager.getIcon("FileView.fileIcon"));
            System.err.println("Fallback icon used due to missing src/assets/file_flat.png");
        }
        iconLabel.setPreferredSize(new Dimension(100, 100));
        iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel nameLabel = new JLabel(entry.docName);
        nameLabel.setFont(new Font("Arial", Font.BOLD, 14));
        nameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        nameLabel.setHorizontalAlignment(SwingConstants.CENTER);

        // Add this to the createDocumentCard method (after creating the nameLabel)
        JButton deleteButton = new JButton("Delete");
        deleteButton.setFont(new Font("Arial", Font.PLAIN, 10));
        deleteButton.setBackground(new Color(255, 100, 100));
        deleteButton.setForeground(Color.WHITE);
        deleteButton.setFocusPainted(false);
        deleteButton.setAlignmentX(Component.CENTER_ALIGNMENT);

        deleteButton.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(
                    DocumentsFrame.this,
                    "Are you sure you want to delete '" + entry.docName + "'?",
                    "Confirm Delete",
                    JOptionPane.YES_NO_OPTION);

            if (confirm == JOptionPane.YES_OPTION) {
                deleteDocument(entry.docName);
            }
        });

        card.add(Box.createVerticalGlue());
        card.add(iconLabel);
        card.add(Box.createVerticalStrut(10));
        card.add(nameLabel);
        card.add(Box.createVerticalGlue());
        card.add(Box.createVerticalStrut(5));
        card.add(deleteButton);

        card.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    // Double-click from owner's document list = owner role
                    new EditorFrame(entry.docName, userId, entry.role, SERVER_HOST).setVisible(true);
                }
            }
        });

        return card;
    }

    private void createNewDocument() {
        String name = JOptionPane.showInputDialog(this, "Enter Document Name:");
        if (name != null && !name.trim().isEmpty()) {
            try (Socket socket = new Socket(SERVER_HOST, 43013);
                    DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                    DataInputStream in = new DataInputStream(socket.getInputStream())) {

                out.writeUTF("createDocument");
                out.writeInt(userId);
                out.writeUTF(name);
                out.writeUTF("");

                String response = in.readUTF();
                if (response.startsWith("Document created successfully")) {
                    JOptionPane.showMessageDialog(this, response);
                    fetchDocuments();
                } else {
                    JOptionPane.showMessageDialog(this, "Failed to create document.", "Error",
                            JOptionPane.ERROR_MESSAGE);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void deleteDocument(String docName) {
        try (Socket socket = new Socket(SERVER_HOST, 43013);
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                DataInputStream in = new DataInputStream(socket.getInputStream())) {

            out.writeUTF("deleteDocument");
            out.writeInt(userId);
            out.writeUTF(docName);

            String response = in.readUTF();
            if (response.equals("Document deleted successfully")) {
                fetchDocuments(); // Refresh the document list
                JOptionPane.showMessageDialog(this, response);
            } else {
                JOptionPane.showMessageDialog(this, response, "Error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error connecting to server", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void joinDocumentWithCode() {
        String code = JOptionPane.showInputDialog(this, "Enter Session Code:");
        if (code != null && !code.trim().isEmpty()) {
            try (Socket socket = new Socket(SERVER_HOST, 43013);
                    DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                    DataInputStream in = new DataInputStream(socket.getInputStream())) {

                out.writeUTF("joinDocument");
                out.writeInt(userId);
                out.writeUTF(code);

                String response = in.readUTF();
                if (response.startsWith("Document joined successfully")) {
                    String docName = in.readUTF();
                    String docContent = in.readUTF();
                    String role = response.contains("view") ? "viewer" : "editor";
                    new EditorFrame(docName, userId, role, SERVER_HOST).setVisible(true);

                    fetchDocuments();
                } else {
                    JOptionPane.showMessageDialog(this, response, "Error", JOptionPane.ERROR_MESSAGE);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}