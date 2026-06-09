package com.ithows.service;

import com.ithows.ResultMap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

public class WebTermAuthStore {

    private static final Object LOCK = new Object();
    private static final String DEFAULT_USER_ID = "soxuser";
    private static final String DEFAULT_PASSWORD = "sox2018";
    private static boolean initialized = false;

    private static Connection getConnection() throws Exception {
        Class.forName("org.sqlite.JDBC");
        Path dbPath = dataDirectory().resolve("data.db").toAbsolutePath();
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }

    private static Path dataDirectory() throws Exception {
        String explicit = System.getProperty("webterm.dataDir", "").trim();
        Path dir;
        if (explicit.length() > 0) {
            dir = Path.of(explicit);
        } else {
            String catalinaBase = System.getProperty("catalina.base", "").trim();
            dir = catalinaBase.length() > 0
                    ? Path.of(catalinaBase, "webterm-data")
                    : Path.of(System.getProperty("user.dir"));
        }
        Files.createDirectories(dir);
        return dir;
    }

    private static void ensureSchema(Connection conn) throws Exception {
        synchronized (LOCK) {
            if (initialized) {
                return;
            }
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS webterm_users ("
                        + "user_no INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + "user_id TEXT NOT NULL UNIQUE,"
                        + "user_pswd TEXT NOT NULL,"
                        + "user_name TEXT NOT NULL,"
                        + "user_security_level INTEGER NOT NULL DEFAULT 1,"
                        + "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                        + "updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                        + "last_login_at TEXT"
                        + ")");
            }
            ensureDefaultUser(conn);
            initialized = true;
        }
    }

    private static void ensureDefaultUser(Connection conn) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO webterm_users(user_id, user_pswd, user_name, user_security_level, updated_at) "
                        + "VALUES(?,?,?,?,CURRENT_TIMESTAMP) "
                        + "ON CONFLICT(user_id) DO UPDATE SET "
                        + "user_pswd=excluded.user_pswd, "
                        + "user_name=excluded.user_name, "
                        + "user_security_level=excluded.user_security_level, "
                        + "updated_at=CURRENT_TIMESTAMP")) {
            ps.setString(1, DEFAULT_USER_ID);
            ps.setString(2, DEFAULT_PASSWORD);
            ps.setString(3, DEFAULT_USER_ID);
            ps.setInt(4, 1);
            ps.executeUpdate();
        }
    }

    public ResultMap authenticate(String userId, String password) throws Exception {
        try (Connection conn = getConnection()) {
            ensureSchema(conn);
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT user_no, user_id, user_name, user_security_level "
                            + "FROM webterm_users "
                            + "WHERE user_id=? AND user_pswd=? AND user_security_level=1")) {
                ps.setString(1, userId == null ? "" : userId.trim());
                ps.setString(2, password == null ? "" : password);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                    ResultMap user = new ResultMap();
                    user.put("userNo", rs.getInt("user_no"));
                    user.put("userId", rs.getString("user_id"));
                    user.put("userName", rs.getString("user_name"));
                    user.put("userSecurityLevel", rs.getInt("user_security_level"));
                    updateLastLogin(conn, rs.getInt("user_no"));
                    return user;
                }
            }
        }
    }

    private static void updateLastLogin(Connection conn, int userNo) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE webterm_users SET last_login_at=CURRENT_TIMESTAMP WHERE user_no=?")) {
            ps.setInt(1, userNo);
            ps.executeUpdate();
        }
    }
}
