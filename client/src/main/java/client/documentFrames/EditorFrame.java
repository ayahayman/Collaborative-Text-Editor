package client.documentFrames;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.undo.*;
import java.awt.*;
import java.io.*;
import java.net.*;

public class EditorFrame extends JFrame {
    private JTextArea editorArea;
    private JLabel codeLabel;
    private JLabel userListLabel;
    private String docName;
    private int userId;
    private String role;
    private UndoManager undoManager = new UndoManager();

    public EditorFrame(String docName, int userId, String role) {
        this.docName = docName;
        this.userId = userId;
        this.role = role;

        setTitle("Editing: " + docName);
        setSize(900, 600);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        JPanel topPanel = new JPanel(new BorderLayout());
        codeLabel = new JLabel("Document: " + docName);
        codeLabel.setFont(new Font("Arial", Font.ITALIC, 14));
        codeLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        JMenuItem importItem = new JMenuItem("Import");
        JMenuItem exportItem = new JMenuItem("Export");

        fileMenu.add(importItem);
        fileMenu.add(exportItem);
        menuBar.add(fileMenu);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton undoButton = new JButton("Undo");
        JButton redoButton = new JButton("Redo");

        buttonPanel.add(undoButton);
        buttonPanel.add(redoButton);
        menuBar.add(buttonPanel);

        topPanel.add(menuBar, BorderLayout.NORTH);
        topPanel.add(codeLabel, BorderLayout.SOUTH);

        add(topPanel, BorderLayout.NORTH);

        editorArea = new JTextArea();
        editorArea.setFont(new Font("Monospaced", Font.PLAIN, 16));
        editorArea.setLineWrap(true);
        editorArea.setWrapStyleWord(true);
        editorArea.getDocument().addUndoableEditListener(undoManager);

        JScrollPane scrollPane = new JScrollPane(editorArea);
        add(scrollPane, BorderLayout.CENTER);

        userListLabel = new JLabel("Active Users: Anonymous Frog (you)");
        userListLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        userListLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        add(userListLabel, BorderLayout.SOUTH);

        fetchContentAndCode();
        addAutoSave();
    }

    private void fetchContentAndCode() {
        // Fetch document content
        try (Socket socket = new Socket("localhost", 12345);
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                DataInputStream in = new DataInputStream(socket.getInputStream())) {

            out.writeUTF("getDocumentContent");
            out.writeUTF(docName);
            String content = in.readUTF();
            editorArea.setText(content);
            if (role.equals("viewer")) {
                editorArea.setEditable(false);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        // Fetch both editor and viewer codes
        try (Socket socket = new Socket("localhost", 12345);
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                DataInputStream in = new DataInputStream(socket.getInputStream())) {

            out.writeUTF("getSharingCode");
            out.writeUTF(docName);

            String editorCode = in.readUTF();
            String viewerCode = in.readUTF();

            if (role.equals("owner") || role.equals("editor")) {
                codeLabel.setText("Document: " + docName +
                        "  |  Editor Code: " + editorCode +
                        "  |  Viewer Code: " + viewerCode);
            } else {
                codeLabel.setText("Document: " + docName);
            }

        } catch (IOException e) {
            codeLabel.setText("Document: " + docName + "  |  Error fetching codes.");
            e.printStackTrace();
        }
    }

    private void addAutoSave() {
        javax.swing.Timer autoSaveTimer = new javax.swing.Timer(1000, e -> saveContent());
        autoSaveTimer.setRepeats(false);

        editorArea.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                autoSaveTimer.restart();
            }

            public void removeUpdate(DocumentEvent e) {
                autoSaveTimer.restart();
            }

            public void changedUpdate(DocumentEvent e) {
                autoSaveTimer.restart();
            }
        });
    }

    private void saveContent() {
        try (Socket socket = new Socket("localhost", 12345);
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                DataInputStream in = new DataInputStream(socket.getInputStream())) {

            out.writeUTF("saveDocumentContent");
            out.writeUTF(docName);
            out.writeUTF(editorArea.getText());

            in.readUTF();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}