package server;

import org.mindrot.jbcrypt.BCrypt;

import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.List;

public class ClientHandler extends Thread {
    private Socket clientSocket;
    private DataInputStream in;
    private DataOutputStream out;

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
        try {
            this.in = new DataInputStream(socket.getInputStream());
            this.out = new DataOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void run() {
        try {
            String requestType = in.readUTF();
            switch (requestType) {
                case "login":
                    handleLogin();
                    break;
                case "signup":
                    handleSignup();
                    break;
                case "getDocuments":
                    handleDocumentRequest();
                    break;
                case "createDocument":
                    handleCreateDocument();
                    break;
                case "getDocumentContent":
                    handleGetDocumentContent();
                    break;
                case "saveDocumentContent":
                    handleSaveDocumentContent();
                    break;
                case "joinDocument":
                    handleJoinDocument();
                    break;
                case "getSharingCode":
                    handleGetSharingCode();
                    break;
                default:
                    out.writeUTF("Invalid request type");
                    break;
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleLogin() throws IOException {
        String username = in.readUTF();
        String password = in.readUTF();

        boolean isValid = validateUser(username, password);
        if (isValid) {
            int userId = getUserIdByUsername(username);
            out.writeUTF("Login successful");
            out.writeInt(userId);
        } else {
            out.writeUTF("Invalid credentials");
        }
    }

    private void handleSignup() throws IOException {
        String username = in.readUTF();
        String password = in.readUTF();

        if (Database.userExists(username)) {
            out.writeUTF("Username already exists");
        } else {
            String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());
            Database.addUser(username, hashedPassword);
            out.writeUTF("Signup successful");
        }
    }

    private boolean validateUser(String username, String password) {
        String query = "SELECT password_hash FROM users WHERE username = ?";
        try (Connection conn = Database.connect();
                PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String storedHash = rs.getString("password_hash");
                return BCrypt.checkpw(password, storedHash);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    private int getUserIdByUsername(String username) {
        String query = "SELECT id FROM users WHERE username = ?";
        try (Connection conn = Database.connect();
                PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("id");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    private void handleDocumentRequest() throws IOException {
        int userId = in.readInt(); // Read the user ID
        List<Document> documents = Database.getUserDocuments(userId); // Get documents for the user

        out.writeInt(documents.size()); // Send the number of documents to the client

        for (Document doc : documents) {
            // Get the sharing code for the current document and user
            String sharingCode = Database.getSharingCodeByDocumentAndUser(userId, doc.getId());

            System.out.println("Document: " + doc.getName() + ", Sharing Code: " + sharingCode);

            // If sharingCode is null, use "N/A" as the default
            if (sharingCode == null) {
                sharingCode = "N/A";
            }

            // Send the document name and the sharing code to the client
            out.writeUTF(doc.getName());
            out.writeUTF(sharingCode);
        }
    }

    private void handleCreateDocument() throws IOException {
        int userId = in.readInt();
        String docName = in.readUTF();
        String docContent = in.readUTF();

        // Generate both edit and view sharing codes
        String editorCode = Database.generateSharingCode();
        String viewerCode = Database.generateSharingCode();

        // Add the document
        Database.addDocument(docName, docContent, userId);

        // Store both codes with appropriate access
        Database.addSharingCode(docName, userId, editorCode, "edit");
        Database.addSharingCode(docName, userId, viewerCode, "view");

        System.out.println("Document created with editor code: " + editorCode + ", viewer code: " + viewerCode);

        // Send both codes to client
        out.writeUTF("Document created successfully");
        out.writeUTF(editorCode);
        out.writeUTF(viewerCode);
    }

    private void handleGetDocumentContent() throws IOException {
        String docName = in.readUTF();
        String docContent = Database.getDocumentContentByName(docName);

        if (docContent != null) {
            out.writeUTF(docContent);
        } else {
            out.writeUTF(""); // return empty if not found
        }
    }

    // Save document content permanently
    private void handleSaveDocumentContent() throws IOException {
        String name = in.readUTF();
        String newContent = in.readUTF();
        boolean saved = Database.updateDocumentContent(name, newContent); // Save in DB
        out.writeUTF(saved ? "Document updated successfully" : "Failed to save document");
    }

    private void handleJoinDocument() throws IOException {
        int userId = in.readInt();
        String code = in.readUTF();

        String accessType = Database.getAccessTypeByCode(code);

        if (accessType != null) {
            String docName = Database.getDocumentNameByCode(code);
            String docContent = Database.getDocumentContentByName(docName);

            // NEW: assign this user the sharing code if not already assigned
            Database.assignSharingCodeToUser(userId, code);

            out.writeUTF("Document joined successfully with " + accessType + " access.");
            out.writeUTF(docName);
            out.writeUTF(docContent);
        } else {
            out.writeUTF("Invalid or expired session code.");
        }
    }

    private void handleGetSharingCode() throws IOException {
        String docName = in.readUTF();

        // Retrieve both viewer and editor codes
        String editorCode = Database.getCodeByDocNameAndAccess(docName, "edit");
        String viewerCode = Database.getCodeByDocNameAndAccess(docName, "view");

        // Send them back to the client
        out.writeUTF(editorCode != null ? editorCode : "N/A");
        out.writeUTF(viewerCode != null ? viewerCode : "N/A");
    }

}
