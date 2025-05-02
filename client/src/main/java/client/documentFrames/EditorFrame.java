package client.documentFrames;

import crdt.CRDTChar;
import crdt.CRDTDocument;
import java.awt.*;
import java.io.*;
import java.net.*;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;
import javax.swing.undo.*;
import java.nio.file.Files;
import org.apache.poi.xwpf.usermodel.*;
import javax.swing.filechooser.FileNameExtensionFilter;

import java.util.ArrayList;
import java.util.List;

public class EditorFrame extends JFrame {

    private JTextArea editorArea;
    private JLabel codeLabel;
    private JLabel userListLabel;
    private String docName;
    private int userId;
    private String role;
    private UndoManager undoManager = new UndoManager();
    private Socket socket;
    private DataOutputStream out;
    private DataInputStream in;
    private boolean isRemoteEdit = false;
    private CRDTDocument crdtDoc;

    private void connectToServer() {
        try {
            socket = new Socket("localhost", 12345);
            out = new DataOutputStream(socket.getOutputStream());
            in = new DataInputStream(socket.getInputStream());

            // Notify server of intent to sync a document
            out.writeUTF("syncDocument");
            out.writeUTF(docName);
            out.writeInt(userId);
            out.writeUTF(role);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startListeningThread() {
        new Thread(() -> {
            try {
                while (true) {
                    String msgType = in.readUTF();
                    if (msgType.equals("edit")) {
                        int offset = in.readInt();
                        String inserted = in.readUTF();
                        int deletedLength = in.readInt();

                        SwingUtilities.invokeLater(() -> {
                            try {
                                isRemoteEdit = true;

                                if (deletedLength > 0) {
                                    editorArea.getDocument().remove(offset, deletedLength);
                                }

                                if (!inserted.isEmpty()) {
                                    editorArea.getDocument().insertString(offset, inserted, null);
                                }

                            } catch (Exception e) {
                                e.printStackTrace();
                            } finally {
                                isRemoteEdit = false;
                            }
                        });
                    }
                    if (msgType.equals("crdt_insert")) {
                        String value = in.readUTF();
                        int idSize = in.readInt();
                        List<Integer> id = new ArrayList<>();
                        for (int i = 0; i < idSize; i++) {
                            id.add(in.readInt());
                        }
                        String site = in.readUTF();

                        CRDTChar remoteChar = new CRDTChar(value, id, site);
                        crdtDoc.remoteInsert(remoteChar);

                        updateTextArea();
                    }

                    if (msgType.equals("crdt_delete")) {
                        int idSize = in.readInt();
                        List<Integer> id = new ArrayList<>();
                        for (int i = 0; i < idSize; i++) {
                            id.add(in.readInt());
                        }
                        String site = in.readUTF();

                        crdtDoc.deleteById(id, site);
                        updateTextArea();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void enableRealTimeSync() {
        editorArea.getDocument().addUndoableEditListener(e -> {
            if (isRemoteEdit) {
                return;
            }

            UndoableEdit edit = e.getEdit();
            if (edit instanceof AbstractDocument.DefaultDocumentEvent) {
                AbstractDocument.DefaultDocumentEvent event = (AbstractDocument.DefaultDocumentEvent) edit;
                int offset = event.getOffset();
                int length = event.getLength();

                if (event.getType() == DocumentEvent.EventType.INSERT) {
                    try {
                        String inserted = editorArea.getText(offset, length);

                        for (int i = 0; i < inserted.length(); i++) {
                            String ch = String.valueOf(inserted.charAt(i));
                            int logicalPos = offset + i;

                            // Insert into CRDT
                            CRDTChar crdtChar = crdtDoc.localInsert(logicalPos, ch);

                            // Send CRDTChar to server
                            sendInsertCRDT(crdtChar);
                        }

                    } catch (BadLocationException ex) {
                        ex.printStackTrace();
                    }

                } else if (event.getType() == DocumentEvent.EventType.REMOVE) {
                    // You can only delete if the char exists in the CRDT
                    for (int i = 0; i < length; i++) {
                        CRDTChar toDelete = crdtDoc.getCharList().get(offset); // logical match
                        sendDeleteCRDT(toDelete.id, toDelete.siteId);
                        crdtDoc.deleteById(toDelete.id, toDelete.siteId);
                    }
                }

                updateTextArea(); // Refresh editor with CRDT state
            }
        });
    }

    public EditorFrame(String docName, int userId, String role) {
        this.docName = docName;
        this.userId = userId;
        this.role = role;
        this.crdtDoc = new CRDTDocument(String.valueOf(userId));

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

        // Add action listeners for import/export
        importItem.addActionListener(e -> importDocument());
        exportItem.addActionListener(e -> exportDocument());

        JMenuItem deleteItem = new JMenuItem("Delete Document");
        fileMenu.add(deleteItem);
        deleteItem.addActionListener(e -> deleteCurrentDocument());

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

        JScrollPane scrollPane = new JScrollPane(editorArea);
        add(scrollPane, BorderLayout.CENTER);

        userListLabel = new JLabel("Active Users: Anonymous Frog (you)");
        userListLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        userListLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        add(userListLabel, BorderLayout.SOUTH);

        fetchContentAndCode();
        addAutoSave();
        if (!role.equals("viewer")) {
            connectToServer();
            startListeningThread();
            enableRealTimeSync();
        } else {
            connectToServer();
            startListeningThread();
        }
    }

    private void fetchContentAndCode() {
        // Fetch document content
        try (Socket socket = new Socket("localhost", 12345); DataOutputStream out = new DataOutputStream(socket.getOutputStream()); DataInputStream in = new DataInputStream(socket.getInputStream())) {

            out.writeUTF("getDocumentContent");
            out.writeUTF(docName);
            String content = in.readUTF();
            SwingUtilities.invokeLater(() -> editorArea.setText(content));
            if (role.equals("viewer")) {
                editorArea.setEditable(false);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        // Fetch both editor and viewer codes
        try (Socket socket = new Socket("localhost", 12345); DataOutputStream out = new DataOutputStream(socket.getOutputStream()); DataInputStream in = new DataInputStream(socket.getInputStream())) {

            out.writeUTF("getSharingCode");
            out.writeUTF(docName);

            String editorCode = in.readUTF();
            String viewerCode = in.readUTF();

            if (role.equals("owner") || role.equals("editor")) {
                codeLabel.setText("Document: " + docName
                        + "  |  Editor Code: " + editorCode
                        + "  |  Viewer Code: " + viewerCode);
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
        try (Socket socket = new Socket("localhost", 12345); DataOutputStream out = new DataOutputStream(socket.getOutputStream()); DataInputStream in = new DataInputStream(socket.getInputStream())) {

            out.writeUTF("saveDocumentContent");
            out.writeUTF(docName);
            out.writeUTF(editorArea.getText());

            in.readUTF();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void deleteCurrentDocument() {
        if (!role.equals("owner")) {
            JOptionPane.showMessageDialog(this,
                    "Only the document owner can delete this document",
                    "Permission Denied",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to delete '" + docName + "'?",
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION
        );

        if (confirm == JOptionPane.YES_OPTION) {
            try (Socket socket = new Socket("localhost", 12345); DataOutputStream out = new DataOutputStream(socket.getOutputStream()); DataInputStream in = new DataInputStream(socket.getInputStream())) {

                out.writeUTF("deleteDocument");
                out.writeInt(userId);
                out.writeUTF(docName);

                String response = in.readUTF();
                if (response.equals("Document deleted successfully")) {
                    JOptionPane.showMessageDialog(this, response);
                    this.dispose(); // Close the editor window
                } else {
                    JOptionPane.showMessageDialog(this, response, "Error", JOptionPane.ERROR_MESSAGE);
                }
            } catch (IOException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error connecting to server", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void importDocument() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Import Document");

        // Set up file filters
        FileNameExtensionFilter docxFilter = new FileNameExtensionFilter("Word Document (.docx)", "docx");
        FileNameExtensionFilter txtFilter = new FileNameExtensionFilter("Text File (.txt)", "txt");
        fileChooser.addChoosableFileFilter(docxFilter);
        fileChooser.addChoosableFileFilter(txtFilter);
        fileChooser.setFileFilter(docxFilter); // Default to DOCX

        int userSelection = fileChooser.showOpenDialog(this);
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File fileToImport = fileChooser.getSelectedFile();
            try {
                String filePath = fileToImport.getPath().toLowerCase();
                String importedContent;

                if (filePath.endsWith(".docx")) {
                    importedContent = importDocx(fileToImport);
                } else {
                    // Plain text import
                    importedContent = new String(Files.readAllBytes(fileToImport.toPath()));
                }

                editorArea.setText(importedContent);
                saveContent(); // Save the imported content to the server

                JOptionPane.showMessageDialog(this, "Document imported successfully!",
                        "Import Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error importing file: " + ex.getMessage(),
                        "Import Error", JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            }
        }
    }

    private String importDocx(File file) throws IOException {
        StringBuilder content = new StringBuilder();

        try (FileInputStream fis = new FileInputStream(file); XWPFDocument document = new XWPFDocument(fis)) {

            // Process each paragraph in the document
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String text = paragraph.getText();
                if (text != null && !text.trim().isEmpty()) {
                    // Add basic formatting based on paragraph style
                    if (paragraph.getStyle() != null) {
                        String style = paragraph.getStyle().toLowerCase();
                        if (style.contains("heading")) {
                            if (style.contains("1")) {
                                content.append("# ").append(text).append("\n");
                                continue;
                            } else if (style.contains("2")) {
                                content.append("## ").append(text).append("\n");
                                continue;
                            }
                        }
                    }

                    // Check for bold/italic formatting
                    boolean isBold = false;
                    boolean isItalic = false;
                    for (XWPFRun run : paragraph.getRuns()) {
                        if (run.isBold()) {
                            isBold = true;
                        }
                        if (run.isItalic()) {
                            isItalic = true;
                        }
                    }

                    if (isBold && isItalic) {
                        content.append("***").append(text).append("***\n");
                    } else if (isBold) {
                        content.append("**").append(text).append("**\n");
                    } else if (isItalic) {
                        content.append("*").append(text).append("*\n");
                    } else {
                        content.append(text).append("\n");
                    }
                }
            }

            // Process tables if needed
            for (XWPFTable table : document.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        content.append(cell.getText()).append("\t");
                    }
                    content.append("\n");
                }
                content.append("\n");
            }
        }

        return content.toString();
    }

    private void exportDocument() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Export Document");

        // Set up file filters
        FileNameExtensionFilter docxFilter = new FileNameExtensionFilter("Word Document (.docx)", "docx");
        FileNameExtensionFilter txtFilter = new FileNameExtensionFilter("Text File (.txt)", "txt");
        fileChooser.addChoosableFileFilter(docxFilter);
        fileChooser.addChoosableFileFilter(txtFilter);
        fileChooser.setFileFilter(docxFilter); // Default to DOCX

        fileChooser.setSelectedFile(new File(docName + ".docx"));

        int userSelection = fileChooser.showSaveDialog(this);
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File fileToExport = fileChooser.getSelectedFile();
            String filePath = fileToExport.getPath();

            // Ensure proper extension
            if (fileChooser.getFileFilter() == docxFilter && !filePath.toLowerCase().endsWith(".docx")) {
                fileToExport = new File(filePath + ".docx");
            } else if (fileChooser.getFileFilter() == txtFilter && !filePath.toLowerCase().endsWith(".txt")) {
                fileToExport = new File(filePath + ".txt");
            }

            try {
                if (fileChooser.getFileFilter() == docxFilter) {
                    exportAsDocx(fileToExport);
                } else {
                    // Plain text export
                    Files.write(fileToExport.toPath(), editorArea.getText().getBytes());
                }

                JOptionPane.showMessageDialog(this, "Document exported successfully!",
                        "Export Success", JOptionPane.INFORMATION_MESSAGE);

                // Optionally open the exported file
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(fileToExport);
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error exporting file: " + ex.getMessage(),
                        "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void exportAsDocx(File file) throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            // Create a paragraph for the document title
            // XWPFParagraph titleParagraph = document.createParagraph();
            // titleParagraph.setAlignment(ParagraphAlignment.CENTER);

            // XWPFRun titleRun = titleParagraph.createRun();
            // titleRun.setText(docName);
            // titleRun.setBold(true);
            // titleRun.setFontSize(16);
            // Add some space after title
            // document.createParagraph();
            // Process the content
            String[] lines = editorArea.getText().split("\n");

            for (String line : lines) {
                XWPFParagraph paragraph = document.createParagraph();
                XWPFRun run = paragraph.createRun();

                // Basic formatting - you can enhance this to detect markdown or other formatting
                if (line.startsWith("# ")) {
                    run.setText(line.substring(2));
                    run.setBold(true);
                    run.setFontSize(14);
                } else if (line.startsWith("## ")) {
                    run.setText(line.substring(3));
                    run.setBold(true);
                    run.setItalic(true);
                    run.setFontSize(12);
                } else {
                    run.setText(line);
                }
            }

            // Save the document
            try (FileOutputStream out = new FileOutputStream(file)) {
                document.write(out);
            }
        }
    }

//Helper methods
    private void sendInsertCRDT(CRDTChar c) {
        try {
            out.writeUTF("crdt_insert");
            out.writeUTF(c.value);
            out.writeInt(c.id.size());
            for (int i : c.id) {
                out.writeInt(i);
            }
            out.writeUTF(c.siteId);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendDeleteCRDT(List<Integer> id, String siteId) {
        try {
            out.writeUTF("crdt_delete");
            out.writeInt(id.size());
            for (int i : id) {
                out.writeInt(i);
            }
            out.writeUTF(siteId);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void updateTextArea() {
        isRemoteEdit = true;

        //  Get current cursor position
        int caret = editorArea.getCaretPosition();

        editorArea.setText(crdtDoc.toPlainText());

        //  Adjust cursor to stay near where it was
        int newLength = editorArea.getText().length();
        caret = Math.min(caret, newLength); // Prevent going out of bounds

        editorArea.setCaretPosition(caret);

        isRemoteEdit = false;
    }

}
