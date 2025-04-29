// server/src/db/Database.java
package db;

import java.sql.*;
import java.util.UUID;

public class Database {
    private static final String DB_URL = "jdbc:sqlite:collabeditor.db";
    private Connection connection;

    public Database() throws SQLException {
        this.connection = DriverManager.getConnection(DB_URL);
        initializeDatabase();
    }

    private void initializeDatabase() throws SQLException {
        String createUsersTable = "CREATE TABLE IF NOT EXISTS users (" +
                "id TEXT PRIMARY KEY," +
                "username TEXT UNIQUE NOT NULL," +
                "salt TEXT NOT NULL," +
                "hashed_password TEXT NOT NULL" +
                ")";
        
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createUsersTable);
        }
    }

    public boolean createUser(String username, String salt, String hashedPassword) throws SQLException {
        String sql = "INSERT INTO users(id, username, salt, hashed_password) VALUES(?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, UUID.randomUUID().toString());
            pstmt.setString(2, username);
            pstmt.setString(3, salt);
            pstmt.setString(4, hashedPassword);
            return pstmt.executeUpdate() > 0;
        }
    }

    public UserData getUser(String username) throws SQLException {
        String sql = "SELECT salt, hashed_password FROM users WHERE username = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new UserData(rs.getString("salt"), rs.getString("hashed_password"));
            }
            return null;
        }
    }

    public static class UserData {
        public final String salt;
        public final String hashedPassword;

        public UserData(String salt, String hashedPassword) {
            this.salt = salt;
            this.hashedPassword = hashedPassword;
        }
    }

    public void close() throws SQLException {
        if (connection != null) {
            connection.close();
        }
    }
}