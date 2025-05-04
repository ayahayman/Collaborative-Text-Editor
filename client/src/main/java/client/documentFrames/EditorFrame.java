package client.documentFrames;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Shape;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.geom.Rectangle2D;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;
import javax.swing.undo.UndoableEdit;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import crdt.CRDTChar;
import crdt.CRDTDocument;

public class EditorFrame extends JFrame {

    private JTextArea editorArea;
    private JLabel codeLabel;
    private JPanel topPanel;
    private JLabel userListLabel;
    private String docName;
    private int userId;
    private String role;
    private Socket socket;
    private DataOutputStream out;
    private DataInputStream in;
    private boolean isRemoteEdit = false;
    private CRDTDocument crdtDoc;
    private final Map<Integer, CursorData> remoteCursors = new HashMap<>();
    private DefaultListModel<String> activeUserListModel;
    private JList<String> activeUserList;
    private static String SERVER_HOST;

    private static class CursorData {
        List<Integer> crdtId;
        Color color;

        CursorData(List<Integer> crdtId, Color color) {
            this.crdtId = new ArrayList<>(crdtId);
            this.color = color;
        }
    }

    private void connectToServer() {
        try {
            socket = new Socket(SERVER_HOST, 42512);
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

                        updateTextArea(true);
                    }

                    if (msgType.equals("crdt_delete")) {
                        int idSize = in.readInt();
                        List<Integer> id = new ArrayList<>();
                        for (int i = 0; i < idSize; i++) {
                            id.add(in.readInt());
                        }
                        String site = in.readUTF();

                        crdtDoc.deleteById(id, site);
                        updateTextArea(true);
                    }
                    if (msgType.equals("cursor_update")) {
                        int remoteUserId = in.readInt();
                        int idSize = in.readInt();
                        List<Integer> crdtId = new ArrayList<>();
                        for (int i = 0; i < idSize; i++) {
                            crdtId.add(in.readInt());
                        }
                        String colorHex = in.readUTF();
                        Color color = Color.decode(colorHex);

                        remoteCursors.put(remoteUserId, new CursorData(crdtId, color));

                        SwingUtilities.invokeLater(this::repaintRemoteCursors);
                    }
                    if (msgType.equals("remove_cursor")) {
                        int remoteUserId = in.readInt();

                        SwingUtilities.invokeLater(() -> {
                            remoteCursors.remove(remoteUserId);
                            repaintRemoteCursors();
                        });
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

                            // Update own cursor to follow inserted char
                            List<CRDTChar> updated = crdtDoc.getCharList();
                            int newPos = updated.indexOf(crdtChar);
                            List<Integer> nextId = crdtChar.id;
                            if (newPos + 1 < updated.size()) {
                                nextId = updated.get(newPos + 1).id;
                            } else {
                                // Cursor is after the last character
                                nextId = List.of(-1); // Special cursor marker (handled in repaintRemoteCursors)
                            }

                            remoteCursors.put(userId, new CursorData(nextId, getOwnCursorColor()));
                            sendCursorUpdate(nextId);

                        }

                    } catch (BadLocationException ex) {
                        ex.printStackTrace();
                    }

                } else if (event.getType() == DocumentEvent.EventType.REMOVE) {
                    List<CRDTChar> chars = crdtDoc.getCharList();

                    for (int i = 0; i < length; i++) {
                        int targetIndex = Math.min(offset, chars.size() - 1); // Clamp to last valid index
                        if (targetIndex >= 0 && targetIndex < chars.size()) {
                            CRDTChar toDelete = chars.get(targetIndex);
                            sendDeleteCRDT(toDelete.id, toDelete.siteId);
                            crdtDoc.deleteById(toDelete.id, toDelete.siteId);
                        }
                    }

                    // Update cursor position safely
                    List<CRDTChar> updated = crdtDoc.getCharList();
                    List<Integer> nextId;

                    if (updated.isEmpty()) {
                        nextId = List.of(-1); // Empty document, cursor at end
                    } else if (offset >= updated.size()) {
                        nextId = List.of(-1); // Cursor is logically after last character
                    } else {
                        nextId = updated.get(offset).id;
                    }

                    remoteCursors.put(userId, new CursorData(nextId, getOwnCursorColor()));
                    sendCursorUpdate(nextId);
                }

                updateTextArea(true); // Refresh editor with CRDT state
            }
        });
    }

    public EditorFrame(String docName, int userId, String role, String serverHost) {
        this.SERVER_HOST = serverHost;
        this.docName = docName;
        this.userId = userId;
        this.role = role;
        this.crdtDoc = new CRDTDocument(String.valueOf(userId));

        setTitle("Editing: " + docName);
        setSize(900, 600);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        topPanel = new JPanel(new BorderLayout());
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

        activeUserListModel = new DefaultListModel<>();
        activeUserList = new JList<>(activeUserListModel);
        activeUserList.setFont(new Font("Arial", Font.PLAIN, 12));
        activeUserList.setBorder(BorderFactory.createTitledBorder("Active Users"));
        activeUserList.setBackground(new Color(245, 245, 245));
        activeUserList.setPreferredSize(new Dimension(150, 0)); // Side panel width

        add(activeUserList, BorderLayout.EAST);

        fetchContentAndCode();
        fetchActiveUsers();
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                sendDisconnectSignal();
            }
        });

        new javax.swing.Timer(3000, e -> fetchActiveUsers()).start();
        addAutoSave();
        if (!role.equals("viewer")) {
            connectToServer();
            performCRDTSync();
            startListeningThread();
            enableRealTimeSync();
        } else {
            connectToServer();
            performCRDTSync();
            startListeningThread();
        }
        editorArea.addCaretListener(e -> {
            if (!isRemoteEdit) {
                editorArea.getCaret().setVisible(true); // show caret when user moves it
                updateOwnCursorCRDTIdFromCaret();
            }
        });

    }

    private Color getOwnCursorColor() {
        CursorData data = remoteCursors.get(userId);
        return (data != null) ? data.color : Color.BLACK; // fallback
    }

    private void fetchContentAndCode() {
        // Fetch document content
        try (Socket socket = new Socket(SERVER_HOST, 42512);
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                DataInputStream in = new DataInputStream(socket.getInputStream())) {

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
        try (Socket socket = new Socket(SERVER_HOST, 42512);
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                DataInputStream in = new DataInputStream(socket.getInputStream())) {

            out.writeUTF("getSharingCode");
            out.writeUTF(docName);

            String editorCode = in.readUTF();
            String viewerCode = in.readUTF();

            JPanel codePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            codePanel.setOpaque(false);

            codePanel.add(new JLabel("Document: " + docName));

            if (role.equals("owner") || role.equals("editor")) {
                JLabel editorCodeLabel = new JLabel(" | Editor Code: " + editorCode + " ");
                JButton copyEditorBtn = new JButton("Copy");
                copyEditorBtn.setFont(new Font("Arial", Font.PLAIN, 10));
                copyEditorBtn.addActionListener(e -> copyToClipboard(editorCode));

                JLabel viewerCodeLabel = new JLabel(" | Viewer Code: " + viewerCode + " ");
                JButton copyViewerBtn = new JButton("Copy");
                copyViewerBtn.setFont(new Font("Arial", Font.PLAIN, 10));
                copyViewerBtn.addActionListener(e -> copyToClipboard(viewerCode));

                codePanel.add(editorCodeLabel);
                codePanel.add(copyEditorBtn);
                codePanel.add(viewerCodeLabel);
                codePanel.add(copyViewerBtn);
            }

            topPanel.add(codePanel, BorderLayout.SOUTH); // replace previous codeLabel line

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
        try (Socket socket = new Socket(SERVER_HOST, 42512);
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
                JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            try (Socket socket = new Socket(SERVER_HOST, 42512);
                    DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                    DataInputStream in = new DataInputStream(socket.getInputStream())) {

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
                        content.append("").append(text).append("\n");
                    } else if (isBold) {
                        content.append("").append(text).append("\n");
                    } else if (isItalic) {
                        content.append("").append(text).append("\n");
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

                // Basic formatting - you can enhance this to detect markdown or other
                // formatting
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

    // Helper methods
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

    private void performCRDTSync() {
        try {
            out.writeUTF("crdt_sync");
            int count = in.readInt();

            for (int i = 0; i < count; i++) {
                String value = in.readUTF();
                int idSize = in.readInt();
                List<Integer> id = new ArrayList<>();
                for (int j = 0; j < idSize; j++) {
                    id.add(in.readInt());
                }
                String site = in.readUTF();

                CRDTChar c = new CRDTChar(value, id, site);
                crdtDoc.remoteInsert(c);
            }
            updateTextArea(false);
            editorArea.setCaretPosition(0);
            editorArea.getCaret().setVisible(false);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void updateTextArea(boolean restoreCaret) {
        isRemoteEdit = true;
        int caret = editorArea.getCaretPosition();

        String newText = crdtDoc.toPlainText();
        if (!editorArea.getText().equals(newText)) {
            editorArea.setText(newText);
        }

        int newLength = editorArea.getText().length();
        caret = Math.min(caret, newLength);

        if (restoreCaret) {
            editorArea.setCaretPosition(caret);
        }

        // Delay repaint of remote cursors so caret restores first
        SwingUtilities.invokeLater(this::repaintRemoteCursors);

        isRemoteEdit = false;
    }

    private void repaintRemoteCursors() {
        try {
            Highlighter highlighter = editorArea.getHighlighter();
            highlighter.removeAllHighlights();

            for (CursorData data : remoteCursors.values()) {
                int index = -1;
                List<CRDTChar> list = crdtDoc.getCharList();

                // Special case: after last character
                if (data.crdtId.size() == 1 && data.crdtId.get(0) == -1) {
                    index = list.size(); // virtual position after end
                } else {
                    // Find index of CRDT ID in the character list
                    for (int i = 0; i < list.size(); i++) {
                        if (list.get(i).id.equals(data.crdtId)) {
                            index = i;
                            break;
                        }
                    }
                }

                if (index != -1) {
                    int pos = Math.min(index, editorArea.getText().length());

                    editorArea.getHighlighter().addHighlight(
                            pos, pos, new ThinCursorPainter(data.color));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static class ThinCursorPainter implements Highlighter.HighlightPainter {
        private final Color color;

        public ThinCursorPainter(Color color) {
            this.color = color;
        }

        @Override
        public void paint(Graphics g, int p0, int p1, Shape bounds, JTextComponent c) {
            try {
                Rectangle2D r = c.modelToView2D(p0); // Modern, safe alternative
                g.setColor(color);
                g.fillRect((int) r.getX(), (int) r.getY(), 2, (int) r.getHeight()); // Thin vertical bar
            } catch (BadLocationException | IllegalArgumentException e) {
            }
        }
    }

    private void updateOwnCursorCRDTIdFromCaret() {
        int caret = editorArea.getCaretPosition();
        List<CRDTChar> chars = crdtDoc.getCharList();

        List<Integer> id;
        if (caret >= chars.size()) {
            id = List.of(-1); // Sentinel to indicate "after last char"
        } else {
            id = chars.get(caret).id;
        }

        remoteCursors.put(userId, new CursorData(id, getOwnCursorColor()));
        sendCursorUpdate(id);
    }

    private void sendCursorUpdate(List<Integer> id) {
        try {
            out.writeUTF("cursor_update");
            out.writeInt(userId);
            out.writeUTF(docName);
            out.writeInt(id.size());
            for (int i : id) {
                out.writeInt(i);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void fetchActiveUsers() {
        new Thread(() -> {
            try (Socket socket = new Socket(SERVER_HOST, 42512);
                    DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                    DataInputStream in = new DataInputStream(socket.getInputStream())) {

                out.writeUTF("getActiveUsers");
                out.writeUTF(docName);

                int count = in.readInt();
                List<String> userIds = new ArrayList<>();

                for (int i = 0; i < count; i++) {
                    String username = in.readUTF();
                    userIds.add(username);
                }

                SwingUtilities.invokeLater(() -> {
                    activeUserListModel.clear();
                    for (String user : userIds) {
                        activeUserListModel.addElement(user);
                    }
                });

            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void sendDisconnectSignal() {
        try {
            out.writeUTF("disconnectFromDocument");
            out.writeUTF(docName);
            out.writeInt(userId);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void copyToClipboard(String text) {
        Toolkit.getDefaultToolkit()
                .getSystemClipboard()
                .setContents(new StringSelection(text), null);
        JOptionPane.showMessageDialog(this, "Copied to clipboard: " + text);
    }

}