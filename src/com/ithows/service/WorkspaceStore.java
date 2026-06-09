package com.ithows.service;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Files;
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
    private static final String DEFAULT_FONT_FAMILY = "\"JetBrains Mono\", \"Cascadia Mono\", Consolas, \"Courier New\", monospace";
    private static final Set<String> ALLOWED_WEB_MONO_FONTS = Set.of(
            DEFAULT_FONT_FAMILY,
            "\"Fira Code\", \"Cascadia Mono\", Consolas, \"Courier New\", monospace",
            "\"Source Code Pro\", \"Cascadia Mono\", Consolas, \"Courier New\", monospace",
            "\"Roboto Mono\", \"Cascadia Mono\", Consolas, \"Courier New\", monospace",
            "\"IBM Plex Mono\", \"Cascadia Mono\", Consolas, \"Courier New\", monospace",
            "\"Noto Sans Mono\", \"Cascadia Mono\", Consolas, \"Courier New\", monospace",
            "\"Inconsolata\", \"Cascadia Mono\", Consolas, \"Courier New\", monospace",
            "\"Ubuntu Mono\", \"Cascadia Mono\", Consolas, \"Courier New\", monospace",
            "\"Space Mono\", \"Cascadia Mono\", Consolas, \"Courier New\", monospace",
            "\"Azeret Mono\", \"Cascadia Mono\", Consolas, \"Courier New\", monospace"
    );

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
                st.executeUpdate("CREATE TABLE IF NOT EXISTS ssh_servers ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + "host TEXT NOT NULL,"
                        + "port INTEGER NOT NULL DEFAULT 22,"
                        + "username TEXT NOT NULL,"
                        + "password TEXT NOT NULL DEFAULT '',"
                        + "private_key TEXT NOT NULL DEFAULT '',"
                        + "private_key_passphrase TEXT NOT NULL DEFAULT '',"
                        + "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                        + "updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                        + "UNIQUE(host, port, username)"
                        + ")");
                addColumnIfMissing(st, "ssh_servers", "private_key", "TEXT NOT NULL DEFAULT ''");
                addColumnIfMissing(st, "ssh_servers", "private_key_passphrase", "TEXT NOT NULL DEFAULT ''");
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

    private static void addColumnIfMissing(Statement st, String table, String column, String definition) throws Exception {
        try (ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    return;
                }
            }
        }
        st.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
    }

    public JSONArray listServers() throws Exception {
        try (Connection conn = getConnection()) {
            ensureSchema(conn);
            JSONArray arr = new JSONArray();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, host, port, username, password, private_key, private_key_passphrase, created_at, updated_at "
                            + "FROM ssh_servers ORDER BY username, host, port");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    JSONObject o = new JSONObject();
                    o.put("id", rs.getLong("id"));
                    o.put("host", rs.getString("host"));
                    o.put("port", rs.getInt("port"));
                    o.put("username", rs.getString("username"));
                    o.put("password", rs.getString("password"));
                    o.put("privateKey", rs.getString("private_key"));
                    o.put("privateKeyPassphrase", rs.getString("private_key_passphrase"));
                    o.put("createdAt", rs.getString("created_at"));
                    o.put("updatedAt", rs.getString("updated_at"));
                    arr.put(o);
                }
            }
            return arr;
        }
    }

    public JSONObject saveServer(String host, int port, String username, String password,
                                 String privateKey, String privateKeyPassphrase) throws Exception {
        try (Connection conn = getConnection()) {
            ensureSchema(conn);
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO ssh_servers(host, port, username, password, private_key, private_key_passphrase, updated_at) "
                            + "VALUES(?,?,?,?,?,?,CURRENT_TIMESTAMP) "
                            + "ON CONFLICT(host, port, username) DO UPDATE SET "
                            + "password=excluded.password, "
                            + "private_key=excluded.private_key, "
                            + "private_key_passphrase=excluded.private_key_passphrase, "
                            + "updated_at=CURRENT_TIMESTAMP")) {
                ps.setString(1, host);
                ps.setInt(2, port);
                ps.setString(3, username);
                ps.setString(4, password == null ? "" : password);
                ps.setString(5, privateKey == null ? "" : privateKey);
                ps.setString(6, privateKeyPassphrase == null ? "" : privateKeyPassphrase);
                ps.executeUpdate();
            }
            return findServer(conn, host, port, username);
        }
    }

    public JSONObject saveServerPrivateKey(String host, int port, String username,
                                           String privateKey, String privateKeyPassphrase) throws Exception {
        try (Connection conn = getConnection()) {
            ensureSchema(conn);
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO ssh_servers(host, port, username, password, private_key, private_key_passphrase, updated_at) "
                            + "VALUES(?,?,?,'',?,?,CURRENT_TIMESTAMP) "
                            + "ON CONFLICT(host, port, username) DO UPDATE SET "
                            + "private_key=excluded.private_key, "
                            + "private_key_passphrase=excluded.private_key_passphrase, "
                            + "updated_at=CURRENT_TIMESTAMP")) {
                ps.setString(1, host);
                ps.setInt(2, port);
                ps.setString(3, username);
                ps.setString(4, privateKey == null ? "" : privateKey);
                ps.setString(5, privateKeyPassphrase == null ? "" : privateKeyPassphrase);
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
                "SELECT id, host, port, username, password, private_key, private_key_passphrase, created_at, updated_at "
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
                    o.put("privateKey", rs.getString("private_key"));
                    o.put("privateKeyPassphrase", rs.getString("private_key_passphrase"));
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
        settings.put("terminalFontFamily", DEFAULT_FONT_FAMILY);
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
            return DEFAULT_FONT_FAMILY;
        }
        String fontFamily = unescapeHtml(value.trim());
        if (ALLOWED_WEB_MONO_FONTS.contains(fontFamily)) {
            return fontFamily;
        }
        return DEFAULT_FONT_FAMILY;
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
