package server;

import org.mindrot.jbcrypt.BCrypt;

import crdt.CRDTChar;

import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

public class ClientHandler extends Thread {

    private Socket clientSocket;
    private DataInputStream in;
    private DataOutputStream out;
    private String currentDocument = null;
    private int userId = -1; // Used for tracking the user
    private String role = null; // Used for tracking the user's role (editor/viewer)
    private boolean realTimeMode = false;
    private String sessionId = null;
    private long lastActive = System.currentTimeMillis();
    private Queue<Message> pendingMessages = new ConcurrentLinkedQueue<>();
    private boolean disconnected = false;
    private String initialCommand = null; // To handle first command after reconnect

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
        try {
            this.in = new DataInputStream(socket.getInputStream());
            this.out = new DataOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // For reconnecting an existing session
    public ClientHandler(Socket socket, String sessionId) {
        this(socket);
        this.sessionId = sessionId;
        ClientHandler oldHandler = CollabServer.sessionMap.get(sessionId);
        if (oldHandler != null) {
            // Transfer state from old handler
            this.userId = oldHandler.userId;
            this.currentDocument = oldHandler.currentDocument;
            this.role = oldHandler.role;
            this.pendingMessages = oldHandler.pendingMessages;
            this.realTimeMode = oldHandler.realTimeMode;

            // Update active editors
            if (currentDocument != null) {
                CollabServer.activeEditors
                        .getOrDefault(currentDocument, new CopyOnWriteArrayList<>())
                        .remove(oldHandler);
                CollabServer.activeEditors
                        .getOrDefault(currentDocument, new CopyOnWriteArrayList<>())
                        .add(this);
            }
        }
    }

    public void setInitialCommand(String command) {
        this.initialCommand = command;
    }

    @Override
    public void run() {
        try {
            // If reconnecting, process any pending messages
            if (sessionId != null && !pendingMessages.isEmpty()) {
                processPendingMessages();
            }

            // Handle initial command if we have one
            if (initialCommand != null) {
                processCommand(initialCommand);
                initialCommand = null;
            }

            while (!disconnected) {
                String requestType = null;
                try {
                    requestType = in.readUTF();
                    lastActive = System.currentTimeMillis();
                } catch (EOFException eof) {
                    // Client disconnected
                    handleDisconnection();
                    break;
                } catch (IOException ioe) {
                    // Socket error/connection reset
                    handleDisconnection();
                    break;
                }

                processCommand(requestType);
            }
        } catch (IOException e) {
            e.printStackTrace();
            handleDisconnection();
        }
    }

    private void processCommand(String requestType) throws IOException {
        System.out.println("Received request: " + requestType);

        switch (requestType) {
            case "login":
                handleLogin();
                break;
            case "signup":
                handleSignup();
                break;
            case "reconnect":
                handleReconnect();
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
            case "cursor_update":
                handleCursorUpdate();
                break;
            case "getActiveUsers":
                handleGetActiveUsers();
                break;
            case "disconnectFromDocument":
                handleDisconnectFromDocument();
                break;

            default:
                out.writeUTF("Invalid request type");
                break;
        }
    }

    private void handleDisconnection() {
        disconnected = true;

        // Check if we should maintain this session for reconnection
        if (userId != -1 && sessionId != null) {
            System.out.println("Client disconnected, maintaining session for 5 minutes: " + sessionId);
            // Don't remove from collections yet, wait for session timeout
            // Session cleanup is handled by the session monitor thread
        } else {
            // Final cleanup
            cleanupResources();
        }
    }

    public void cleanupResources() {
        if (currentDocument != null) {
            CollabServer.activeEditors.getOrDefault(currentDocument, new CopyOnWriteArrayList<>()).remove(this);
        }

        if (sessionId != null) {
            CollabServer.sessionMap.remove(sessionId);
        }

        try {
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void processPendingMessages() {
        try {
            // Send count of pending messages
            out.writeInt(pendingMessages.size());

            // Send all pending messages
            while (!pendingMessages.isEmpty()) {
                Message msg = pendingMessages.poll();
                if (msg != null) {
                    msg.sendTo(out);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleReconnect() throws IOException {
        String sessionId = in.readUTF();

        ClientHandler existingHandler = CollabServer.sessionMap.get(sessionId);
        if (existingHandler != null &&
                System.currentTimeMillis() - existingHandler.lastActive <= 5 * 60 * 1000) {

            // Valid reconnection
            this.sessionId = sessionId;
            this.userId = existingHandler.userId;
            this.currentDocument = existingHandler.currentDocument;
            this.role = existingHandler.role;
            this.pendingMessages = existingHandler.pendingMessages;
            this.realTimeMode = existingHandler.realTimeMode;

            // Replace old handler in collections
            if (currentDocument != null) {
                CollabServer.activeEditors
                        .getOrDefault(currentDocument, new CopyOnWriteArrayList<>())
                        .remove(existingHandler);
                CollabServer.activeEditors
                        .getOrDefault(currentDocument, new CopyOnWriteArrayList<>())
                        .add(this);
            }

            // Update session map
            CollabServer.sessionMap.put(sessionId, this);

            // Notify client of successful reconnection
            out.writeUTF("RECONNECT_SUCCESS");
            out.writeInt(userId);
            out.writeUTF(currentDocument != null ? currentDocument : "");
            out.writeUTF(role != null ? role : "");

            // Process any pending messages
            processPendingMessages();
        } else {
            // Invalid or expired session
            out.writeUTF("RECONNECT_FAILED");
        }
    }

    private void handleLogin() throws IOException {
        String username = in.readUTF();
        String password = in.readUTF();

        boolean isValid = validateUser(username, password);
        if (isValid) {
            int userId = getUserIdByUsername(username);
            this.userId = userId;

            // Generate session ID for reconnection
            if (sessionId == null) {
                sessionId = UUID.randomUUID().toString();
                CollabServer.sessionMap.put(sessionId, this);
            }

            out.writeUTF("Login successful");
            out.writeInt(userId);
            out.writeUTF(sessionId); // Send session ID to client
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
            out.writeUTF("owner"); // Send the role as "owner" for now

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
        this.role = in.readUTF();
        this.realTimeMode = true;

        // Insert this block right here:
        CollabServer.activeEditors.putIfAbsent(currentDocument, new CopyOnWriteArrayList<>());
        CollabServer.activeEditors.get(currentDocument).add(this);

        CollabServer.cursorColors.putIfAbsent(currentDocument, new HashMap<>());

        Map<Integer, String> docColors = CollabServer.cursorColors.get(currentDocument);
        if (!docColors.containsKey(userId)) {
            String[] palette = { "#FF0000", "#0000FF", "#008000", "#FFA500", "#800080", "#00CED1" };
            docColors.put(userId, palette[docColors.size() % palette.length]);
        }

        System.out.println("User " + userId + " started real-time sync on document: " + currentDocument);
    }

    private void handleEditBroadcast() throws IOException {
        int offset = in.readInt();
        String inserted = in.readUTF();
        int deletedLength = in.readInt();

        // Broadcast this edit to all other clients editing this document
        for (ClientHandler client : CollabServer.activeEditors.getOrDefault(currentDocument,
                new CopyOnWriteArrayList<>())) {
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
        for (ClientHandler client : CollabServer.activeEditors.getOrDefault(currentDocument,
                new CopyOnWriteArrayList<>())) {
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
        for (ClientHandler client : CollabServer.activeEditors.getOrDefault(currentDocument,
                new CopyOnWriteArrayList<>())) {
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

    private void handleCursorUpdate() throws IOException {
        int senderId = in.readInt();
        String doc = in.readUTF();
        int idSize = in.readInt();
        List<Integer> crdtId = new ArrayList<>();
        for (int i = 0; i < idSize; i++) {
            crdtId.add(in.readInt());
        }

        String color = CollabServer.cursorColors.getOrDefault(doc, new HashMap<>()).getOrDefault(senderId, "#000000");

        for (ClientHandler client : CollabServer.activeEditors.getOrDefault(doc, new CopyOnWriteArrayList<>())) {
            if (client.realTimeMode) {
                try {
                    client.out.writeUTF("cursor_update");
                    client.out.writeInt(senderId);
                    client.out.writeInt(idSize);
                    for (int i : crdtId) {
                        client.out.writeInt(i);
                    }
                    client.out.writeUTF(color);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void handleGetActiveUsers() throws IOException {
        String docName = in.readUTF();
        List<ClientHandler> handlers = CollabServer.activeEditors.getOrDefault(docName, new CopyOnWriteArrayList<>());

        out.writeInt(handlers.size());
        for (ClientHandler handler : handlers) {
            String username = Database.getUsernameById(handler.userId);
            out.writeUTF(username);
        }
    }

    private void handleDisconnectFromDocument() throws IOException {
        String doc = in.readUTF();
        int uid = in.readInt();

        List<ClientHandler> handlers = CollabServer.activeEditors.getOrDefault(doc, new CopyOnWriteArrayList<>());
        handlers.removeIf(h -> h.userId == uid);

        // Notify other users to remove the cursor
        for (ClientHandler client : handlers) {
            if (client.realTimeMode) {
                try {
                    client.out.writeUTF("remove_cursor");
                    client.out.writeInt(uid); // Tell them which user to remove
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        System.out.println("User " + uid + " left document: " + doc);
    }

    // Add this method to queue a message for disconnected users
    public void queueMessage(Message message) {
        pendingMessages.add(message);
    }

    public long getLastActive() {
        return lastActive;
    }

    public String getSessionId() {
        return sessionId;
    }

    public int getUserId() {
        return userId;
    }

    public String getCurrentDocument() {
        return currentDocument;
    }

    public DataOutputStream getOutputStream() {
        return out;
    }

    public boolean isConnected() {
        return !disconnected && clientSocket != null && !clientSocket.isClosed();
    }

}
