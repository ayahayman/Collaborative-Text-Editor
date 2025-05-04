package server;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.mindrot.jbcrypt.BCrypt;

public class Database {

    private static final String DB_URL = "jdbc:sqlite:editor.db"; // Adjust the path as needed

    // Method to connect to the SQLite database
    public static Connection connect() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC"); // Force load the SQLite driver
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return DriverManager.getConnection(DB_URL);
    }

    // Create the users table if it doesn't exist
    public static void createUsersTable() {
        String createTableSQL = "CREATE TABLE IF NOT EXISTS users ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "username TEXT NOT NULL, "
                + "password_hash TEXT NOT NULL);";
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSQL); // Executes the SQL query to create the table if it doesn't exist
            System.out.println("Users table created or already exists.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Check if the user already exists in the database
    public static boolean userExists(String username) {
        String query = "SELECT * FROM users WHERE username = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            return rs.next(); // Returns true if the username exists
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    // Insert a new user into the database
    public static void addUser(String username, String passwordHash) {
        String insertSQL = "INSERT INTO users (username, password_hash) VALUES (?, ?)";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {
            pstmt.setString(1, username);
            pstmt.setString(2, passwordHash);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Check if the user exists and the password is correct
    public static boolean validateUser(String username, String passwordHash) {
        System.out.println("Validating user: " + username + ", Password hash: " + passwordHash);
        String query = "SELECT password_hash FROM users WHERE username = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                // Retrieve the stored hashed password from the database
                String storedHash = rs.getString("password_hash");
                System.out.println("Stored hash: " + storedHash);
                System.out.println("Entered hash: " + passwordHash);
                // Compare the entered password with the stored hashed password
                return BCrypt.checkpw(passwordHash, storedHash); // Returns true if passwords match
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false; // Invalid credentialsF
    }

    public static void createDocumentsTable() {

        String createTableSQL = "CREATE TABLE IF NOT EXISTS documents ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "name TEXT NOT NULL, "
                + "content TEXT, "
                + "creator_id INTEGER NOT NULL, "
                + "FOREIGN KEY (creator_id) REFERENCES users(id));"; // Ensuring that the creator_id links to the users
        // table
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSQL);
            System.out.println("Documents table created or already exists.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        ;
        // recreateDocumentsTable(); // Recreate the documents table to ensure the
        // correct schema
    }

    public static void createSharingTable() {
        String createTableSQL = "CREATE TABLE IF NOT EXISTS sharing_codes ("
                + "document_id INTEGER NOT NULL, "
                + "user_id INTEGER NOT NULL, "
                + "code TEXT NOT NULL, "
                + "access_type TEXT NOT NULL, "
                + // "view" or "edit"
                "FOREIGN KEY (document_id) REFERENCES documents(id), "
                + "FOREIGN KEY (user_id) REFERENCES users(id));";
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSQL);
            System.out.println("Sharing codes table created or already exists.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        // resetSharingCodesTable();
    }

    public static void addDocument(String name, String content, int creatorId) {
        System.out.println("Adding document: " + name + ", Content: " + content + ", Creator ID: " + creatorId);
        String insertSQL = "INSERT INTO documents (name, content, creator_id) VALUES (?, ?, ?)";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {
            pstmt.setString(1, name); // Document name
            pstmt.setString(2, content); // Document content
            pstmt.setInt(3, creatorId); // Creator ID (the user who created the document)

            pstmt.executeUpdate(); // Execute the insertion
            System.out.println("Document added with creator ID: " + creatorId);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Get all documents for a user
    public static List<Document> getUserDocuments(int userId) {
        List<Document> documents = new ArrayList<>();

        String query = """
                    SELECT DISTINCT d.id, d.name, d.content
                    FROM documents d
                    LEFT JOIN sharing_codes sc ON d.id = sc.document_id
                    WHERE d.creator_id = ? OR sc.user_id = ?
                """;

        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, userId);
            pstmt.setInt(2, userId);

            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                int id = rs.getInt("id");
                String name = rs.getString("name");
                String content = rs.getString("content");
                documents.add(new Document(id, name, content));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return documents;
    }

    // Method to generate a unique sharing code
    public static String generateSharingCode() {
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder code = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 8; i++) { // Generate an 8-character code
            int index = random.nextInt(characters.length());
            code.append(characters.charAt(index));
        }
        return code.toString();
    }

    // Method to add sharing code
    public static void addSharingCode(String docName, int userId, String sharingCode, String accessType) {
        String insertSQL = "INSERT INTO sharing_codes (document_id, user_id, code, access_type) VALUES (?, ?, ?, ?)";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {
            // Retrieve the document ID based on the document name
            String query = "SELECT id FROM documents WHERE name = ?";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, docName);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    int documentId = rs.getInt("id");

                    // Insert the sharing code for this document
                    pstmt.setInt(1, documentId);
                    pstmt.setInt(2, userId);
                    pstmt.setString(3, sharingCode);
                    pstmt.setString(4, accessType);
                    pstmt.executeUpdate();
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Verify a sharing code and get the access type (view/edit)
    public static String getAccessTypeByCode(String code) {
        String query = "SELECT access_type FROM sharing_codes WHERE code = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, code);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("access_type");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String getDocumentContentByName(String docName) {
        String query = "SELECT content FROM documents WHERE name = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, docName);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("content"); // Return the document content
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null; // Return null if document not found
    }

    public static void recreateDocumentsTable() {
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            // Drop the existing documents table if it exists
            stmt.executeUpdate("DROP TABLE IF EXISTS documents;");
            System.out.println("Dropped existing documents table (if it existed).");

            // Create the new documents table with the correct schema
            String createTableSQL = "CREATE TABLE documents ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "name TEXT NOT NULL, "
                    + "content TEXT, "
                    + "creator_id INTEGER NOT NULL, "
                    + // Correct column name 'creator_id'
                    "FOREIGN KEY (creator_id) REFERENCES users(id));"; // Link to users table

            stmt.executeUpdate(createTableSQL);
            System.out.println("Documents table recreated with 'creator_id' column.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void resetSharingCodesTable() {
        String dropTableSQL = "DROP TABLE IF EXISTS sharing_codes";
        String createTableSQL = "CREATE TABLE IF NOT EXISTS sharing_codes ("
                + "document_id INTEGER NOT NULL, "
                + "user_id INTEGER NOT NULL, "
                + "code TEXT NOT NULL, "
                + "access_type TEXT NOT NULL, "
                + // "view" or "edit"
                "FOREIGN KEY (document_id) REFERENCES documents(id), "
                + "FOREIGN KEY (user_id) REFERENCES users(id));";

        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            // Drop the existing table if it exists
            stmt.executeUpdate(dropTableSQL);
            System.out.println("Dropped existing sharing_codes table (if it existed).");

            // Recreate the table with the correct schema
            stmt.executeUpdate(createTableSQL);
            System.out.println("Sharing codes table recreated.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static String getDocumentNameByCode(String code) {
        String query = "SELECT documents.name FROM documents JOIN sharing_codes ON documents.id = sharing_codes.document_id WHERE sharing_codes.code = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, code);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("name");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String getSharingCodeByDocumentAndUser(int userId, int documentId) {
        String query = "SELECT code FROM sharing_codes WHERE user_id = ? AND document_id = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, userId); // Set user ID
            pstmt.setInt(2, documentId); // Set document ID
            ResultSet rs = pstmt.executeQuery();

            // If sharing code exists for the document and user, return it
            if (rs.next()) {
                return rs.getString("code");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null; // Return null if no sharing code found
    }

    public static boolean updateDocumentContent(String docName, String newContent) {
        String sql = "UPDATE documents SET content = ? WHERE name = ?";
        try (Connection conn = connect(); PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, newContent);
            stmt.setString(2, docName);
            return stmt.executeUpdate() > 0;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static String getCodeByDocNameAndAccess(String docName, String accessType) {
        String query = "SELECT sc.code FROM sharing_codes sc "
                + "JOIN documents d ON sc.document_id = d.id "
                + "WHERE d.name = ? AND sc.access_type = ? LIMIT 1";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, docName);
            pstmt.setString(2, accessType);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("code");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void assignSharingCodeToUser(int userId, String code) {
        String checkSQL = "SELECT * FROM sharing_codes WHERE user_id = ? AND code = ?";
        String updateSQL = "INSERT INTO sharing_codes (document_id, user_id, code, access_type) "
                + "SELECT document_id, ?, code, access_type FROM sharing_codes WHERE code = ? LIMIT 1";

        try (Connection conn = connect()) {
            // Check if already exists
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSQL)) {
                checkStmt.setInt(1, userId);
                checkStmt.setString(2, code);
                ResultSet rs = checkStmt.executeQuery();
                if (rs.next()) {
                    return; // Already assigned

                            }}

            // Assign new sharing code entry to this user
            try (PreparedStatement insertStmt = conn.prepareStatement(updateSQL)) {
                insertStmt.setInt(1, userId);
                insertStmt.setString(2, code);
                insertStmt.executeUpdate();
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static boolean isDocumentOwner(int userId, String docName) {
        String query = "SELECT creator_id FROM documents WHERE name = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, docName);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("creator_id") == userId;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static boolean deleteDocument(String docName) {
        // First get the document ID
        String getDocIdQuery = "SELECT id FROM documents WHERE name = ?";
        try (Connection conn = connect()) {
            int docId = -1;

            // Get document ID
            try (PreparedStatement pstmt = conn.prepareStatement(getDocIdQuery)) {
                pstmt.setString(1, docName);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    docId = rs.getInt("id");
                } else {
                    return false; // Document not found
                }
            }

            // Delete sharing codes first (due to foreign key constraint)
            String deleteSharingQuery = "DELETE FROM sharing_codes WHERE document_id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(deleteSharingQuery)) {
                pstmt.setInt(1, docId);
                pstmt.executeUpdate();
            }

            // Then delete the document
            String deleteDocQuery = "DELETE FROM documents WHERE id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(deleteDocQuery)) {
                pstmt.setInt(1, docId);
                return pstmt.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static String getUserRoleForDocument(int userId, int documentId) {
        String query = "SELECT d.creator_id, sc.access_type "
                + "FROM documents d "
                + "LEFT JOIN sharing_codes sc ON d.id = sc.document_id AND sc.user_id = ? "
                + "WHERE d.id = ?";

        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, userId);
            pstmt.setInt(2, documentId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                int creatorId = rs.getInt("creator_id");
                String accessType = rs.getString("access_type");
                if (creatorId == userId) {
                    return "owner";
                }
                if ("edit".equals(accessType)) {
                    return "editor";
                }
                if ("view".equals(accessType)) {
                    return "viewer";
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "viewer"; // Default fallback
    }

    public static String getUsernameById(int userId) {
        String query = "SELECT username FROM users WHERE id = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, userId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("username");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

}
