package com.ithows.service;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;

public class WorkspaceStore {

    private static final Object LOCK = new Object();
    private static final Set<String> ALLOWED_CDN_FONTS = Set.of(
            "\"JetBrains Mono\"",
            "\"Fira Code\"",
            "\"Source Code Pro\"",
            "\"Roboto Mono\"",
            "\"IBM Plex Mono\"",
            "\"Noto Sans Mono\"",
            "\"Inconsolata\"",
            "\"Ubuntu Mono\"",
            "\"Space Mono\"",
            "\"Azeret Mono\""
    );

    private static boolean initialized = false;

    private static Connection getConnection() throws Exception {
        Path dbPath = Path.of(System.getProperty("user.dir"), "data.db").toAbsolutePath();
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }

    private static void ensureSchema(Connection conn) throws Exception {
        synchronized (LOCK) {
            if (initialized) {
                return;
            }
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS ssh_servers ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + "host TEXT NOT NULL,"
                        + "port INTEGER NOT NULL DEFAULT 22,"
                        + "username TEXT NOT NULL,"
                        + "password TEXT NOT NULL DEFAULT '',"
                        + "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                        + "updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                        + "UNIQUE(host, port, username)"
                        + ")");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS quick_commands ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + "command TEXT NOT NULL UNIQUE,"
                        + "sort_order INTEGER NOT NULL DEFAULT 0,"
                        + "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                        + "updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP"
                        + ")");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS workspace_settings ("
                        + "setting_key TEXT PRIMARY KEY,"
                        + "setting_value TEXT NOT NULL,"
                        + "updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP"
                        + ")");
                initialized = true;
            }
        }
    }

    public JSONArray listServers() throws Exception {
        try (Connection conn = getConnection()) {
            ensureSchema(conn);
            JSONArray arr = new JSONArray();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, host, port, username, password, created_at, updated_at "
                            + "FROM ssh_servers ORDER BY username, host, port");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    JSONObject o = new JSONObject();
                    o.put("id", rs.getLong("id"));
                    o.put("host", rs.getString("host"));
                    o.put("port", rs.getInt("port"));
                    o.put("username", rs.getString("username"));
                    o.put("password", rs.getString("password"));
                    o.put("createdAt", rs.getString("created_at"));
                    o.put("updatedAt", rs.getString("updated_at"));
                    arr.put(o);
                }
            }
            return arr;
        }
    }

    public JSONObject saveServer(String host, int port, String username, String password) throws Exception {
        try (Connection conn = getConnection()) {
            ensureSchema(conn);
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO ssh_servers(host, port, username, password, updated_at) VALUES(?,?,?,?,CURRENT_TIMESTAMP) "
                            + "ON CONFLICT(host, port, username) DO UPDATE SET "
                            + "password=excluded.password, updated_at=CURRENT_TIMESTAMP")) {
                ps.setString(1, host);
                ps.setInt(2, port);
                ps.setString(3, username);
                ps.setString(4, password == null ? "" : password);
                ps.executeUpdate();
            }
            return findServer(conn, host, port, username);
        }
    }

    public boolean deleteServer(long id) throws Exception {
        try (Connection conn = getConnection()) {
            ensureSchema(conn);
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM ssh_servers WHERE id=?")) {
                ps.setLong(1, id);
                return ps.executeUpdate() > 0;
            }
        }
    }

    public JSONArray listCommands() throws Exception {
        try (Connection conn = getConnection()) {
            ensureSchema(conn);
            JSONArray arr = new JSONArray();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, command, sort_order, created_at, updated_at "
                            + "FROM quick_commands ORDER BY sort_order, id");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    JSONObject o = new JSONObject();
                    o.put("id", rs.getLong("id"));
                    o.put("command", rs.getString("command"));
                    o.put("sortOrder", rs.getInt("sort_order"));
                    o.put("createdAt", rs.getString("created_at"));
                    o.put("updatedAt", rs.getString("updated_at"));
                    arr.put(o);
                }
            }
            return arr;
        }
    }

    public JSONObject saveCommand(String command) throws Exception {
        try (Connection conn = getConnection()) {
            ensureSchema(conn);
            int sortOrder = nextCommandSortOrder(conn);
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO quick_commands(command, sort_order, updated_at) VALUES(?,?,CURRENT_TIMESTAMP) "
                            + "ON CONFLICT(command) DO UPDATE SET updated_at=CURRENT_TIMESTAMP")) {
                ps.setString(1, command);
                ps.setInt(2, sortOrder);
                ps.executeUpdate();
            }
            return findCommand(conn, command);
        }
    }

    public boolean deleteCommand(long id) throws Exception {
        try (Connection conn = getConnection()) {
            ensureSchema(conn);
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM quick_commands WHERE id=?")) {
                ps.setLong(1, id);
                return ps.executeUpdate() > 0;
            }
        }
    }

    public JSONObject getSettings() throws Exception {
        try (Connection conn = getConnection()) {
            ensureSchema(conn);
            return getSettings(conn);
        }
    }

    public JSONObject saveSettings(String terminalFontFamily, int terminalFontSize) throws Exception {
        try (Connection conn = getConnection()) {
            ensureSchema(conn);
            saveSetting(conn, "terminalFontFamily", normalizeFontFamily(terminalFontFamily));
            saveSetting(conn, "terminalFontSize", String.valueOf(normalizeFontSize(terminalFontSize)));
            return getSettings(conn);
        }
    }

    public JSONObject exportServersJson() throws Exception {
        JSONObject out = new JSONObject();
        out.put("exportedAt", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        out.put("servers", listServers());
        return out;
    }

    private JSONObject getSettings(Connection conn) throws Exception {
        JSONObject settings = defaultSettings();
        try (PreparedStatement ps = conn.prepareStatement("SELECT setting_key, setting_value FROM workspace_settings");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                settings.put(rs.getString("setting_key"), rs.getString("setting_value"));
            }
        }
        settings.put("terminalFontFamily", normalizeFontFamily(settings.optString("terminalFontFamily", "")));
        settings.put("terminalFontSize", parseFontSize(settings.optString("terminalFontSize", "14")));
        return settings;
    }

    private JSONObject findServer(Connection conn, String host, int port, String username) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, host, port, username, password, created_at, updated_at "
                        + "FROM ssh_servers WHERE host=? AND port=? AND username=?")) {
            ps.setString(1, host);
            ps.setInt(2, port);
            ps.setString(3, username);
            try (ResultSet rs = ps.executeQuery()) {
                JSONObject o = new JSONObject();
                if (rs.next()) {
                    o.put("id", rs.getLong("id"));
                    o.put("host", rs.getString("host"));
                    o.put("port", rs.getInt("port"));
                    o.put("username", rs.getString("username"));
                    o.put("password", rs.getString("password"));
                    o.put("createdAt", rs.getString("created_at"));
                    o.put("updatedAt", rs.getString("updated_at"));
                }
                return o;
            }
        }
    }

    private JSONObject findCommand(Connection conn, String command) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, command, sort_order, created_at, updated_at FROM quick_commands WHERE command=?")) {
            ps.setString(1, command);
            try (ResultSet rs = ps.executeQuery()) {
                JSONObject o = new JSONObject();
                if (rs.next()) {
                    o.put("id", rs.getLong("id"));
                    o.put("command", rs.getString("command"));
                    o.put("sortOrder", rs.getInt("sort_order"));
                    o.put("createdAt", rs.getString("created_at"));
                    o.put("updatedAt", rs.getString("updated_at"));
                }
                return o;
            }
        }
    }

    private int nextCommandSortOrder(Connection conn) throws Exception {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(sort_order), 0) + 10 AS next_order FROM quick_commands")) {
            if (rs.next()) {
                return rs.getInt("next_order");
            }
            return 10;
        }
    }

    private static JSONObject defaultSettings() {
        JSONObject settings = new JSONObject();
        settings.put("terminalFontFamily", "\"JetBrains Mono\"");
        settings.put("terminalFontSize", 14);
        return settings;
    }

    private static void saveSetting(Connection conn, String key, String value) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO workspace_settings(setting_key, setting_value, updated_at) VALUES(?,?,CURRENT_TIMESTAMP) "
                        + "ON CONFLICT(setting_key) DO UPDATE SET setting_value=excluded.setting_value, updated_at=CURRENT_TIMESTAMP")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        }
    }

    private static String normalizeFontFamily(String value) {
        if (value == null || value.isBlank()) {
            return "\"JetBrains Mono\"";
        }
        String fontFamily = unescapeHtml(value.trim());
        if (ALLOWED_CDN_FONTS.contains(fontFamily)) {
            return fontFamily;
        }
        return "\"JetBrains Mono\"";
    }

    private static String unescapeHtml(String value) {
        return value
                .replace("&quot;", "\"")
                .replace("&#34;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&");
    }

    private static int normalizeFontSize(int value) {
        if (value < 10) return 10;
        if (value > 24) return 24;
        return value;
    }

    private static int parseFontSize(String value) {
        try {
            return normalizeFontSize(Integer.parseInt(value));
        } catch (Exception e) {
            return 14;
        }
    }
}
