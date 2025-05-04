package server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.mindrot.jbcrypt.BCrypt;

import crdt.CRDTChar;

public class ClientHandler extends Thread {

    private Socket clientSocket;
    private DataInputStream in;
    private DataOutputStream out;
    private String currentDocument = null;
    private int userId = -1; // Used for tracking the user
    private String role = null; // Used for tracking the user's role (editor/viewer)
    private boolean realTimeMode = false;

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
        try {
            this.in = new DataInputStream(socket.getInputStream());
            this.out = new DataOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            System.out.println("👤 ClientHandler started for: " + clientSocket.getInetAddress());
            while (true) {
                String requestType = in.readUTF();
                System.out.println("📩 Received request: " + requestType);
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
                    case "deleteDocument":
                        handleDeleteDocument();
                        break;
                    case "joinDocument":
                        handleJoinDocument();
                        break;
                    case "getSharingCode":
                        handleGetSharingCode();
                        break;
                    case "syncDocument":
                        handleSyncDocument();
                        break;
                    case "edit":
                        handleEditBroadcast();
                        break;
                    case "crdt_insert":
                        handleCRDTInsert();
                        break;
                    case "crdt_delete":
                        handleCRDTDelete();
                        break;
                    case "crdt_sync":
                        handleCRDTSync();
                        break;

                    default:
                    System.out.println("⚠️ Unknown request type: " + requestType);
                    break;
                }
            }
        }  catch (EOFException eof) {
            System.out.println("🔌 Client disconnected unexpectedly: " + clientSocket.getInetAddress());
        } catch (IOException e) {
            System.err.println("❌ IOException for client: " + clientSocket.getInetAddress());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("🔥 Unexpected exception in ClientHandler: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                // Remove from active editors
                if (currentDocument != null) {
                    CopyOnWriteArrayList<ClientHandler> editors = CollabServer.activeEditors.get(currentDocument);
                    if (editors != null) {
                        editors.remove(this);
                        System.out.println("🧹 Removed client from active editors of: " + currentDocument);
                    }
                }
    
                // Close socket
                if (clientSocket != null && !clientSocket.isClosed()) {
                    clientSocket.close();
                    System.out.println("🔒 Closed connection: " + clientSocket.getInetAddress());
                }
    
            } catch (IOException e) {
                System.err.println("❌ Failed to close client socket");
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
        try (Connection conn = Database.connect(); PreparedStatement pstmt = conn.prepareStatement(query)) {
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
        try (Connection conn = Database.connect(); PreparedStatement pstmt = conn.prepareStatement(query)) {
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

    private void handleDeleteDocument() throws IOException {
        int userId = in.readInt();
        String docName = in.readUTF();

        boolean isOwner = Database.isDocumentOwner(userId, docName);
        if (!isOwner) {
            out.writeUTF("You don't have permission to delete this document");
            return;
        }

        boolean deleted = Database.deleteDocument(docName);
        if (deleted) {
            out.writeUTF("Document deleted successfully");
        } else {
            out.writeUTF("Failed to delete document");
        }
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

    private void handleSyncDocument() throws IOException {
        this.currentDocument = in.readUTF();
        this.userId = in.readInt();
        this.role = in.readUTF();             // role
        this.realTimeMode = true;

        // Add this client to the global document editor list
        CollabServer.activeEditors.putIfAbsent(currentDocument, new CopyOnWriteArrayList<>());
        CollabServer.activeEditors.get(currentDocument).add(this);

        System.out.println("User " + userId + " started real-time sync on document: " + currentDocument);
    }

    private void handleEditBroadcast() throws IOException {
        int offset = in.readInt();
        String inserted = in.readUTF();
        int deletedLength = in.readInt();

        // Broadcast this edit to all other clients editing this document
        for (ClientHandler client : CollabServer.activeEditors.getOrDefault(currentDocument, new CopyOnWriteArrayList<>())) {
            if (client != this && client.realTimeMode) {
                try {
                    client.out.writeUTF("edit");
                    client.out.writeInt(offset);
                    client.out.writeUTF(inserted);
                    client.out.writeInt(deletedLength);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void handleCRDTInsert() throws IOException {
        String value = in.readUTF();
        int idSize = in.readInt();
        List<Integer> id = new ArrayList<>();
        for (int i = 0; i < idSize; i++) {
            id.add(in.readInt());
        }
        String site = in.readUTF();

        // Save it in CRDT storage
        CRDTChar newChar = new CRDTChar(value, id, site);
        CollabServer.crdtStorage.putIfAbsent(currentDocument, new ArrayList<>());
        List<CRDTChar> crdtList = CollabServer.crdtStorage.get(currentDocument);

        if (!crdtList.contains(newChar)) {
            crdtList.add(newChar);
        }

        // Broadcast to other clients (same as before)
        for (ClientHandler client : CollabServer.activeEditors.getOrDefault(currentDocument, new CopyOnWriteArrayList<>())) {
            if (client != this && client.realTimeMode) {
                try {
                    client.out.writeUTF("crdt_insert");
                    client.out.writeUTF(value);
                    client.out.writeInt(idSize);
                    for (int i : id) {
                        client.out.writeInt(i);
                    }
                    client.out.writeUTF(site);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void handleCRDTDelete() throws IOException {
        int idSize = in.readInt();
        List<Integer> id = new ArrayList<>();
        for (int i = 0; i < idSize; i++) {
            id.add(in.readInt());
        }
        String site = in.readUTF();
        System.out.println("Delete from " + site + " at ID " + id);
        // Broadcast to other clients
        for (ClientHandler client : CollabServer.activeEditors.getOrDefault(currentDocument, new CopyOnWriteArrayList<>())) {
            if (client != this && client.realTimeMode) {
                try {
                    client.out.writeUTF("crdt_delete");
                    client.out.writeInt(idSize);
                    for (int i : id) {
                        client.out.writeInt(i);
                    }
                    client.out.writeUTF(site);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void handleCRDTSync() throws IOException {
        List<CRDTChar> crdtList = CollabServer.crdtStorage.getOrDefault(currentDocument, new ArrayList<>());
        out.writeInt(crdtList.size());
        for (CRDTChar c : crdtList) {
            out.writeUTF(c.value);
            out.writeInt(c.id.size());
            for (int i : c.id) {
                out.writeInt(i);
            }
            out.writeUTF(c.siteId);
        }
    }
}
