package server;

import java.sql.*;

public class Database {
    private static final String DB_URL = "jdbc:sqlite:editor.db"; // Adjust the path as needed

    // Method to connect to the SQLite database
    public static Connection connect() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    // Create the users table if it doesn't exist
    public static void createUsersTable() {
        String createTableSQL = "CREATE TABLE IF NOT EXISTS users (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "username TEXT NOT NULL, " +
                "password_hash TEXT NOT NULL);";
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
        String query = "SELECT * FROM users WHERE username = ? AND password_hash = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, username);
            pstmt.setString(2, passwordHash);
            ResultSet rs = pstmt.executeQuery();
            return rs.next(); // Returns true if a user with matching credentials is found
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }
}
