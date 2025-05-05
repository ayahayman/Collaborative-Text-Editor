package server;

import java.io.*;
import java.net.Socket;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.mindrot.jbcrypt.BCrypt;
import crdt.CRDTChar;

public class ClientHandler extends Thread {
    private final Socket clientSocket;
    private DataInputStream in;
    private DataOutputStream out;
    private String currentDocument = null;
    private int userId = -1;
    private String role = null;
    private boolean realTimeMode = false;
    
    // Reconnection and state management
    private final AtomicLong lastHeartbeat = new AtomicLong(System.currentTimeMillis());
    private final AtomicBoolean disconnected = new AtomicBoolean(false);
    private final BlockingQueue<String> pendingMessages = new LinkedBlockingQueue<>();
    private final String clientId;
    private final Random random = new Random();

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
        this.clientId = generateClientId();
        try {
            this.in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            this.out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        } catch (IOException e) {
            System.err.println("Error initializing streams for client: " + e.getMessage());
        }
    }

    private String generateClientId() {
        return clientSocket.getInetAddress() + ":" + clientSocket.getPort() + "-" + 
               System.currentTimeMillis() + "-" + random.nextInt(1000);
    }

    @Override
    public void run() {
        try {
            System.out.println("👤 Client connected: " + clientId);
            startHeartbeatMonitor();
            
            while (!clientSocket.isClosed()) {
                String requestType = in.readUTF();
                lastHeartbeat.set(System.currentTimeMillis());
                
                switch (requestType) {
                    case "heartbeat":
                        break; // Just update lastHeartbeat
                        
                    case "reconnect":
                        handleReconnection();
                        break;
                        
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
                        
                    case "cursor_update":
                        handleCursorUpdate();
                        break;
                        
                    case "disconnectFromDocument":
                        handleDisconnectFromDocument();
                        break;
                        
                    case "getActiveUsers":
                        handleGetActiveUsers();
                        break;
                        
                    default:
                        System.out.println("⚠️ Unknown request from " + clientId + ": " + requestType);
                        sendError("Unknown request type");
                        break;
                }
            }
        } catch (EOFException e) {
            System.out.println("🔌 Client disconnected: " + clientId);
        } catch (IOException e) {
            System.err.println("❌ IO Error with client " + clientId + ": " + e.getMessage());
        } catch (Exception e) {
            System.err.println("🔥 Unexpected error with client " + clientId + ": " + e.getMessage());
            e.printStackTrace();
        } finally {
            handleGracefulDisconnection();
            cleanup();
        }
    }

    private void startHeartbeatMonitor() {
        new Thread(() -> {
            while (!clientSocket.isClosed()) {
                try {
                    Thread.sleep(30000); // Check every 30 seconds
                    if (System.currentTimeMillis() - lastHeartbeat.get() > 300000) { // 5 minutes
                        System.out.println("⌛ Client timeout: " + clientId);
                        clientSocket.close();
                        break;
                    }
                } catch (Exception e) {
                    break;
                }
            }
        }).start();
    }

    public long getLastHeartbeat() {
        return lastHeartbeat.get();
    }

    private void handleReconnection() throws IOException {
        String reconnectingClientId = in.readUTF();
        String reconnectingDocName = in.readUTF();
        
        if (CollabServer.activeEditors.containsKey(reconnectingDocName)) {
            out.writeUTF("reconnect_accepted");
            
            // Send pending messages
            synchronized (pendingMessages) {
                out.writeInt(pendingMessages.size());
                for (String msg : pendingMessages) {
                    out.writeUTF(msg);
                }
                pendingMessages.clear();
            }
            out.flush();
            
            // Reintegrate client
            CollabServer.activeEditors.get(reconnectingDocName).add(this);
            this.currentDocument = reconnectingDocName;
            this.disconnected.set(false);
            System.out.println("♻️ Client reconnected: " + reconnectingClientId);
        } else {
            out.writeUTF("reconnect_failed");
            System.out.println("❌ Reconnection failed for: " + reconnectingClientId);
        }
    }

    private void handleLogin() throws IOException {
        String username = in.readUTF();
        String password = in.readUTF();

        boolean isValid = Database.validateUser(username, password);
        if (isValid) {
            this.userId = getUserIdByUsername(username);
            out.writeUTF("Login successful");
            out.writeInt(userId);
        } else {
            out.writeUTF("Invalid credentials");
        }
        out.flush();
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
        out.flush();
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

        String editorCode = Database.generateSharingCode();
        String viewerCode = Database.generateSharingCode();

        Database.addDocument(docName, docContent, userId);
        Database.addSharingCode(docName, userId, editorCode, "edit");
        Database.addSharingCode(docName, userId, viewerCode, "view");

        out.writeUTF("Document created successfully");
        out.writeUTF(editorCode);
        out.writeUTF(viewerCode);
        out.flush();
    }

    private void handleGetDocumentContent() throws IOException {
        String docName = in.readUTF();
        String content = Database.getDocumentContentByName(docName);
        out.writeUTF(content != null ? content : "");
        out.flush();
    }

    private void handleSaveDocumentContent() throws IOException {
        String name = in.readUTF();
        String newContent = in.readUTF();
        boolean saved = Database.updateDocumentContent(name, newContent);
        out.writeUTF(saved ? "Document updated successfully" : "Failed to save document");
        out.flush();
    }

    private void handleDeleteDocument() throws IOException {
        int userId = in.readInt();
        String docName = in.readUTF();

        if (!Database.isDocumentOwner(userId, docName)) {
            out.writeUTF("You don't have permission to delete this document");
            out.flush();
            return;
        }

        boolean deleted = Database.deleteDocument(docName);
        out.writeUTF(deleted ? "Document deleted successfully" : "Failed to delete document");
        out.flush();
    }

    private void handleJoinDocument() throws IOException {
        int userId = in.readInt();
        String code = in.readUTF();

        String accessType = Database.getAccessTypeByCode(code);
        if (accessType != null) {
            String docName = Database.getDocumentNameByCode(code);
            String docContent = Database.getDocumentContentByName(docName);

            Database.assignSharingCodeToUser(userId, code);

            out.writeUTF("Document joined successfully with " + accessType + " access.");
            out.writeUTF(docName);
            out.writeUTF(docContent);
        } else {
            out.writeUTF("Invalid or expired session code.");
        }
        out.flush();
    }

    private void handleGetSharingCode() throws IOException {
        String docName = in.readUTF();
        String editorCode = Database.getCodeByDocNameAndAccess(docName, "edit");
        String viewerCode = Database.getCodeByDocNameAndAccess(docName, "view");

        out.writeUTF(editorCode != null ? editorCode : "N/A");
        out.writeUTF(viewerCode != null ? viewerCode : "N/A");
        out.flush();
    }

    private void handleSyncDocument() throws IOException {
        this.currentDocument = in.readUTF();
        this.userId = in.readInt();
        this.role = in.readUTF();
        this.realTimeMode = true;

        CollabServer.activeEditors.putIfAbsent(currentDocument, new CopyOnWriteArrayList<>());
        CollabServer.activeEditors.get(currentDocument).add(this);

        System.out.println("User " + userId + " started real-time sync on: " + currentDocument);
    }

    private void handleEditBroadcast() throws IOException {
        int offset = in.readInt();
        String inserted = in.readUTF();
        int deletedLength = in.readInt();

        broadcastToDocument("edit|" + offset + "|" + inserted + "|" + deletedLength);
    }

    private void handleCRDTInsert() throws IOException {
        CRDTChar newChar = readCRDTCharFromStream();
        storeCRDTChar(newChar);
        broadcastCRDTOperation("insert", newChar);
    }

    private CRDTChar readCRDTCharFromStream() throws IOException {
        String value = in.readUTF();
        int idSize = in.readInt();
        List<Integer> id = new ArrayList<>(idSize);
        for (int i = 0; i < idSize; i++) {
            id.add(in.readInt());
        }
        String site = in.readUTF();
        return new CRDTChar(value, id, site);
    }

    private void storeCRDTChar(CRDTChar newChar) {
        CollabServer.crdtStorage.putIfAbsent(currentDocument, new ArrayList<>());
        List<CRDTChar> crdtList = CollabServer.crdtStorage.get(currentDocument);

        if (!crdtList.contains(newChar)) {
            crdtList.add(newChar);
        }
    }

    private void broadcastCRDTOperation(String operation, CRDTChar crdtChar) {
        String message = buildCRDTMessage(operation, crdtChar);
        broadcastToDocument(message);
    }

    private String buildCRDTMessage(String operation, CRDTChar crdtChar) {
        StringBuilder sb = new StringBuilder("crdt_")
            .append(operation).append("|")
            .append(crdtChar.value).append("|")
            .append(crdtChar.id.size());
        
        crdtChar.id.forEach(id -> sb.append("|").append(id));
        sb.append("|").append(crdtChar.siteId);
        
        return sb.toString();
    }

    private void handleCRDTDelete() throws IOException {
        int idSize = in.readInt();
        List<Integer> id = new ArrayList<>(idSize);
        for (int i = 0; i < idSize; i++) {
            id.add(in.readInt());
        }
        String site = in.readUTF();

        broadcastToDocument("crdt_delete|" + idSize + "|" + 
            String.join("|", id.stream().map(String::valueOf).toArray(String[]::new)) + 
            "|" + site);
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
        out.flush();
    }

    private void handleCursorUpdate() throws IOException {
        int remoteUserId = in.readInt();
        String docName = in.readUTF();
        int idSize = in.readInt();
        List<Integer> crdtId = new ArrayList<>(idSize);
        for (int i = 0; i < idSize; i++) {
            crdtId.add(in.readInt());
        }
        String colorHex = "#" + Integer.toHexString(remoteUserId * 1000).substring(0, 6);

        broadcastToDocument("cursor_update|" + remoteUserId + "|" + idSize + "|" +
            String.join("|", crdtId.stream().map(String::valueOf).toArray(String[]::new)) + 
            "|" + colorHex);
    }

    private void handleDisconnectFromDocument() throws IOException {
        if (currentDocument != null) {
            CopyOnWriteArrayList<ClientHandler> editors = CollabServer.activeEditors.get(currentDocument);
            if (editors != null) {
                editors.remove(this);
            }
            currentDocument = null;
            out.writeUTF("disconnect_ack");
            out.flush();
        }
    }

    private void handleGetActiveUsers() throws IOException {
        String docName = in.readUTF();
        CopyOnWriteArrayList<ClientHandler> editors = CollabServer.activeEditors.getOrDefault(docName, new CopyOnWriteArrayList<>());
        
        Set<Integer> uniqueUserIds = new HashSet<>();
        for (ClientHandler handler : editors) {
            uniqueUserIds.add(handler.userId);
        }
        
        out.writeInt(uniqueUserIds.size());
        for (int userId : uniqueUserIds) {
            String username = Database.getUsernameById(userId);
            if (username != null) {
                out.writeUTF(username);
            }
        }
        out.flush();
    }

    private void broadcastToDocument(String message) {
        if (currentDocument == null) return;
        
        CopyOnWriteArrayList<ClientHandler> editors = CollabServer.activeEditors.get(currentDocument);
        if (editors != null) {
            editors.forEach(editor -> {
                try {
                    if (!editor.disconnected.get()) {
                        editor.out.writeUTF(message);
                        editor.out.flush();
                    } else {
                        editor.pendingMessages.offer(message);
                    }
                } catch (IOException e) {
                    editor.handleGracefulDisconnection();
                }
            });
        }
    }

    private void handleGracefulDisconnection() {
        if (currentDocument != null && disconnected.compareAndSet(false, true)) {
            CopyOnWriteArrayList<ClientHandler> editors = CollabServer.activeEditors.get(currentDocument);
            if (editors != null) {
                editors.remove(this);
            }
            
            CollabServer.disconnectedClients
                .computeIfAbsent(currentDocument, k -> new CopyOnWriteArrayList<>())
                .add(this);
                
            System.out.println("⏸️ Client paused (may reconnect): " + clientId);
        }
    }

    private void cleanup() {
        try {
            if (currentDocument != null) {
                CollabServer.activeEditors.getOrDefault(currentDocument, new CopyOnWriteArrayList<>()).remove(this);
                CollabServer.disconnectedClients.getOrDefault(currentDocument, new CopyOnWriteArrayList<>()).remove(this);
            }
            
            if (!clientSocket.isClosed()) {
                clientSocket.close();
            }
            System.out.println("🧹 Cleaned up resources for: " + clientId);
        } catch (IOException e) {
            System.err.println("❌ Error cleaning up client " + clientId + ": " + e.getMessage());
        }
    }

    private void sendError(String message) throws IOException {
        out.writeUTF("error");
        out.writeUTF(message);
        out.flush();
    }
}