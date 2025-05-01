package client.documentFrames;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.undo.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.util.Units;

import javax.swing.filechooser.FileNameExtensionFilter;

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

         // Add action listeners for import/export
         importItem.addActionListener(e -> importDocument());
         exportItem.addActionListener(e -> exportDocument()); 

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
        
        try (FileInputStream fis = new FileInputStream(file);
             XWPFDocument document = new XWPFDocument(fis)) {
            
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
                        if (run.isBold()) isBold = true;
                        if (run.isItalic()) isItalic = true;
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

}