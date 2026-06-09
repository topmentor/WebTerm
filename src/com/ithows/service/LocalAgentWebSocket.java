package com.ithows.service;

import org.json.JSONArray;
import org.json.JSONObject;
import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import com.pty4j.WinSize;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.websocket.CloseReason;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

@ServerEndpoint(value = "/local-agent-terminal", configurator = HttpSessionConfigurator.class)
public class LocalAgentWebSocket {

    private static final String CONNECTION_KEY = "localAgentConnection";
    private static final int MAX_TEXT_SIZE = 1024 * 1024;

    @OnOpen
    public void onOpen(Session webSession) {
        webSession.setMaxIdleTimeout(0);
        webSession.setMaxTextMessageBufferSize(MAX_TEXT_SIZE);
        sendStatus(webSession, "READY", "로컬 AI CLI 정보를 기다리는 중입니다.");
    }

    @OnMessage
    public void onMessage(String message, Session webSession) {
        try {
            JSONObject json = new JSONObject(message);
            String type = json.optString("type", "");

            if ("connect".equals(type)) {
                connect(json, webSession);
            } else if ("input".equals(type)) {
                LocalAgentConnection connection = getConnection(webSession);
                if (connection != null) {
                    connection.write(json.optString("data", ""));
                }
            } else if ("resize".equals(type)) {
                LocalAgentConnection connection = getConnection(webSession);
                if (connection != null) {
                    connection.resize(json.optInt("cols", 80), json.optInt("rows", 24));
                }
            } else if ("registerRemote".equals(type)) {
                LocalAgentConnection connection = getConnection(webSession);
                if (connection != null) {
                    connection.registerRemote(json);
                }
            } else if ("disconnect".equals(type)) {
                closeConnection(webSession);
                sendStatus(webSession, "DISCONNECTED", "로컬 AI CLI를 종료했습니다.");
            }
        } catch (Exception e) {
            sendError(webSession, e.getMessage());
        }
    }

    @OnClose
    public void onClose(Session webSession, CloseReason reason) {
        closeConnection(webSession);
    }

    @OnError
    public void onError(Session webSession, Throwable error) {
        sendError(webSession, error.getMessage());
        closeConnection(webSession);
    }

    private void connect(JSONObject json, Session webSession) throws Exception {
        closeConnection(webSession);

        String kind = json.optString("agentKind", "codex").trim().toLowerCase(Locale.ROOT);
        String cwd = json.optString("cwd", "").trim();
        String command = commandFor(kind);
        int cols = json.optInt("cols", 80);
        int rows = json.optInt("rows", 24);

        sendStatus(webSession, "CONNECTING", command + " 로컬 실행 중...");

        LocalAgentConnection connection = new LocalAgentConnection(webSession);
        connection.connect(command, cwd, cols, rows);
        webSession.getUserProperties().put(CONNECTION_KEY, connection);

        sendStatus(webSession, "CONNECTED", command + " 로컬 실행됨");
    }

    private String commandFor(String kind) {
        if ("claude".equals(kind)) {
            return configuredCommand("webterm.claudeCommand", "WEBTERM_CLAUDE_CMD", "claude");
        }
        if ("codex".equals(kind)) {
            return configuredCommand("webterm.codexCommand", "WEBTERM_CODEX_CMD", "codex");
        }
        throw new IllegalArgumentException("지원하지 않는 AI 도구입니다.");
    }

    private String configuredCommand(String propertyName, String envName, String defaultCommand) {
        String value = System.getProperty(propertyName, "").trim();
        if (value.length() == 0) {
            value = System.getenv(envName);
            if (value != null) {
                value = value.trim();
            }
        }
        return value == null || value.length() == 0 ? defaultCommand : value;
    }

    private LocalAgentConnection getConnection(Session webSession) {
        Object value = webSession.getUserProperties().get(CONNECTION_KEY);
        if (value instanceof LocalAgentConnection) {
            return (LocalAgentConnection) value;
        }
        return null;
    }

    private void closeConnection(Session webSession) {
        LocalAgentConnection connection = getConnection(webSession);
        if (connection != null) {
            connection.close();
            webSession.getUserProperties().remove(CONNECTION_KEY);
        }
    }

    private static void sendOutput(Session webSession, String data) {
        JSONObject json = new JSONObject();
        json.put("type", "output");
        json.put("data", data);
        sendJson(webSession, json);
    }

    private static void sendStatus(Session webSession, String state, String message) {
        JSONObject json = new JSONObject();
        json.put("type", "status");
        json.put("state", state);
        json.put("message", message);
        sendJson(webSession, json);
    }

    private static void sendError(Session webSession, String message) {
        JSONObject json = new JSONObject();
        json.put("type", "error");
        json.put("message", message == null ? "알 수 없는 오류가 발생했습니다." : message);
        sendJson(webSession, json);
    }

    private static void sendJson(Session webSession, JSONObject json) {
        if (webSession != null && webSession.isOpen()) {
            synchronized (webSession) {
                try {
                    webSession.getBasicRemote().sendText(json.toString());
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static class LocalAgentConnection {
        private final Session webSession;
        private PtyProcess process;
        private OutputStream processInput;
        private Thread readerThread;
        private Path sshConfigDir;
        private Path sshConfigPath;
        private Path sshControlDir;
        private final Map<String, JSONObject> remoteServers = new HashMap<>();
        private volatile boolean closed;

        LocalAgentConnection(Session webSession) {
            this.webSession = webSession;
        }

        void connect(String command, String cwd, int cols, int rows) throws Exception {
            Map<String, String> env = new HashMap<>(System.getenv());
            env.put("TERM", "xterm-256color");
            env.put("COLORTERM", "truecolor");
            env.put("FORCE_COLOR", "1");
            configureRegisteredSshKeys(env);
            sendLocalSshDiagnostics();
            String directory = null;
            if (cwd.length() > 0) {
                File dir = new File(cwd);
                if (!dir.isDirectory()) {
                    throw new IllegalArgumentException("작업 디렉토리가 존재하지 않습니다: " + cwd);
                }
                directory = dir.getAbsolutePath();
            }

            PtyProcessBuilder builder = new PtyProcessBuilder()
                    .setCommand(localCommand(command))
                    .setEnvironment(env)
                    .setInitialColumns(Math.max(cols, 2))
                    .setInitialRows(Math.max(rows, 1));
            if (directory != null) {
                builder.setDirectory(directory);
            }

            process = builder.start();
            processInput = process.getOutputStream();

            InputStream output = process.getInputStream();
            readerThread = new Thread(new OutputPump(output), "local-agent-output");
            readerThread.setDaemon(true);
            readerThread.start();
        }

        private void sendLocalSshDiagnostics() {
            StringBuilder warnings = new StringBuilder();
            String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            boolean windows = osName.contains("win");

            if (!commandExists("ssh", windows)) {
                warnings.append("[WARN] 로컬 ssh 명령을 찾을 수 없습니다. Codex/Claude가 SSH 리모트에 접근하지 못할 수 있습니다.\r\n");
            }

            Path sshDir = Path.of(System.getProperty("user.home"), ".ssh");
            Path configPath = sshDir.resolve("config");
            Path knownHostsPath = sshDir.resolve("known_hosts");

            if (!Files.exists(configPath)) {
                warnings.append("[WARN] 로컬 ~/.ssh/config 파일이 없습니다. Host alias 기반 접속은 실패할 수 있습니다.\r\n");
            }
            if (!Files.exists(knownHostsPath)) {
                warnings.append("[WARN] 로컬 ~/.ssh/known_hosts 파일이 없습니다. 최초 SSH 접속에서 host key 확인이 필요할 수 있습니다.\r\n");
            }

            if (windows) {
                if (!windowsSshAgentAppearsAvailable()) {
                    warnings.append("[WARN] Windows ssh-agent 서비스가 실행 중인지 확인하지 못했습니다. passphrase key는 사용할 수 없을 수 있습니다.\r\n");
                }
            } else if (!unixSshAgentAppearsAvailable()) {
                warnings.append("[WARN] SSH_AUTH_SOCK 환경 변수가 없습니다. ssh-agent에 등록된 key를 사용할 수 없을 수 있습니다.\r\n");
            }

            if (sshConfigDir != null) {
                warnings.append("[INFO] WebTerm 등록 서버를 임시 SSH config alias로 주입했습니다. $WEBTERM_SSH_CONFIG를 사용할 수 있습니다.\r\n");
            }

            if (warnings.length() > 0) {
                sendOutput(webSession, warnings.toString());
            }
        }

        private boolean commandExists(String command, boolean windows) {
            try {
                ProcessBuilder builder = windows
                        ? new ProcessBuilder("cmd.exe", "/c", "where " + command)
                        : new ProcessBuilder("/bin/sh", "-lc", "command -v " + command);
                Process check = builder.redirectErrorStream(true).start();
                return check.waitFor() == 0;
            } catch (Exception e) {
                return false;
            }
        }

        private boolean unixSshAgentAppearsAvailable() {
            String authSock = System.getenv("SSH_AUTH_SOCK");
            return authSock != null && authSock.trim().length() > 0;
        }

        private boolean windowsSshAgentAppearsAvailable() {
            try {
                Process check = new ProcessBuilder("cmd.exe", "/c", "sc query ssh-agent | find \"RUNNING\"")
                        .redirectErrorStream(true)
                        .start();
                return check.waitFor() == 0;
            } catch (Exception e) {
                return false;
            }
        }

        private void configureRegisteredSshKeys(Map<String, String> env) throws Exception {
            sshConfigDir = Files.createTempDirectory("webterm-ssh-");
            sshConfigPath = sshConfigDir.resolve("config");
            sshControlDir = sshConfigDir.resolve("cm");
            Files.createDirectories(sshControlDir);
            setOwnerOnly(sshControlDir);
            writeSshConfig();
            env.put("GIT_SSH_COMMAND", "ssh -F \"" + sshConfigPath.toAbsolutePath() + "\"");
            env.put("WEBTERM_SSH_CONFIG", sshConfigPath.toAbsolutePath().toString());
        }

        void registerRemote(JSONObject server) throws Exception {
            if (sshConfigDir == null || sshConfigPath == null) {
                return;
            }
            String alias = aliasFor(server);
            if (alias.length() == 0) {
                return;
            }
            remoteServers.put(alias, new JSONObject(server.toString()));
            writeSshConfig();
            sendOutput(webSession, "[INFO] 현재 SSH 서버 alias를 WEBTERM_SSH_CONFIG에 갱신했습니다: " + alias + "\r\n");
            if (server.optString("privateKey", "").trim().length() == 0) {
                sendOutput(webSession, "[WARN] 현재 SSH 서버 alias에는 private key가 없습니다. 로컬 ssh-agent 또는 ~/.ssh/config에 인증 키가 없으면 인증이 실패합니다.\r\n");
            }
        }

        private void writeSshConfig() throws Exception {
            StringBuilder config = new StringBuilder();
            Set<String> hostAliases = new HashSet<>();
            int index = 0;

            for (JSONObject server : remoteServers.values()) {
                appendServerConfig(config, hostAliases, server, index++);
            }

            JSONArray servers = new WorkspaceStore().listServers();
            for (int i = 0; i < servers.length(); i++) {
                JSONObject server = servers.getJSONObject(i);
                appendServerConfig(config, hostAliases, server, index++);
            }

            Files.writeString(sshConfigPath, config.toString(), StandardCharsets.UTF_8);
            setOwnerOnly(sshConfigPath);
        }

        private void appendServerConfig(StringBuilder config, Set<String> hostAliases,
                                        JSONObject server, int index) throws Exception {
            String privateKey = server.optString("privateKey", "").trim();
            String host = server.optString("host", "").trim();
            String username = server.optString("username", "").trim();
            int port = server.optInt("port", 22);
            if (host.length() == 0 || username.length() == 0) {
                return;
            }

            String alias = aliasFor(host, port, username);
            if (!hostAliases.add(alias)) {
                return;
            }
            config.append("Host ").append(alias).append('\n')
                    .append("  IgnoreUnknown ControlMaster,ControlPersist,ControlPath\n")
                    .append("  HostName ").append(host).append('\n')
                    .append("  Port ").append(port).append('\n')
                    .append("  User ").append(username).append('\n')
                    .append("  StrictHostKeyChecking no\n")
                    .append("  ControlMaster auto\n")
                    .append("  ControlPersist 10m\n")
                    .append("  ControlPath \"").append(controlPath(alias)).append("\"\n");

            if (privateKey.length() > 0) {
                Path keyPath = sshConfigDir.resolve("key-" + index);
                Files.writeString(keyPath, privateKey + System.lineSeparator(), StandardCharsets.UTF_8);
                setOwnerOnly(keyPath);
                String identityFile = keyPath.toAbsolutePath().toString().replace('\\', '/');
                config.append("  IdentityFile \"").append(identityFile).append("\"\n")
                        .append("  IdentitiesOnly yes\n");
            }

            config.append('\n');
        }

        private String safeName(String value) {
            return value.replaceAll("[^A-Za-z0-9_.-]", "_");
        }

        private String aliasFor(JSONObject server) {
            String host = server.optString("host", "").trim();
            String username = server.optString("username", "").trim();
            int port = server.optInt("port", 22);
            if (host.length() == 0 || username.length() == 0) {
                return "";
            }
            return aliasFor(host, port, username);
        }

        private String aliasFor(String host, int port, String username) {
            return "webterm-" + safeName(host) + "-" + port + "-" + safeName(username);
        }

        private String controlPath(String alias) {
            Path base = sshControlDir == null ? sshConfigDir : sshControlDir;
            return base.resolve(safeName(alias) + "-%C").toAbsolutePath().toString().replace('\\', '/');
        }

        private void setOwnerOnly(Path path) {
            try {
                if (Files.isDirectory(path)) {
                    Files.setPosixFilePermissions(path, Set.of(
                            PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE,
                            PosixFilePermission.OWNER_EXECUTE
                    ));
                } else {
                    Files.setPosixFilePermissions(path, Set.of(
                            PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE
                    ));
                }
            } catch (Exception ignored) {
            }
        }

        private String[] localCommand(String command) {
            String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            if (osName.contains("win")) {
                String shell = System.getenv("COMSPEC");
                if (shell == null || shell.trim().length() == 0) {
                    shell = "cmd.exe";
                }
                return new String[] {
                        shell,
                        "/k",
                        windowsCommand(command) + " & echo. & echo [WebTerm] AI CLI exited. Local shell is still open."
                };
            }
            return new String[] {
                    "/bin/sh",
                    "-lc",
                    unixCommand(command) + "; printf '\\r\\n[WebTerm] AI CLI exited. Local shell is still open.\\r\\n'; exec \"${SHELL:-/bin/sh}\" -i"
            };
        }

        private String unixCommand(String command) {
            String executable = firstToken(command);
            return "if command -v " + shellQuote(executable) + " >/dev/null 2>&1; then "
                    + command
                    + "; else printf '\\r\\n[ERROR] AI CLI command not found on the WebTerm Tomcat server: "
                    + escapeSingleQuoted(command)
                    + "\\r\\n[ERROR] Install it for the Tomcat user or set -Dwebterm.codexCommand / -Dwebterm.claudeCommand, or WEBTERM_CODEX_CMD / WEBTERM_CLAUDE_CMD.\\r\\n'; fi";
        }

        private String windowsCommand(String command) {
            String executable = firstToken(command);
            return "where " + executable
                    + " >nul 2>nul && " + command
                    + " || echo [ERROR] AI CLI command not found on the WebTerm Tomcat server: " + command
                    + " && echo [ERROR] Install it for the Tomcat service user or set webterm.codexCommand/webterm.claudeCommand.";
        }

        private String firstToken(String command) {
            String trimmed = command == null ? "" : command.trim();
            if (trimmed.startsWith("\"")) {
                int end = trimmed.indexOf('"', 1);
                if (end > 1) {
                    return trimmed.substring(1, end);
                }
            }
            int space = trimmed.indexOf(' ');
            return space > 0 ? trimmed.substring(0, space) : trimmed;
        }

        private String shellQuote(String value) {
            return "'" + escapeSingleQuoted(value) + "'";
        }

        private String escapeSingleQuoted(String value) {
            return String.valueOf(value).replace("'", "'\"'\"'");
        }

        void write(String data) throws Exception {
            if (closed || processInput == null) {
                return;
            }
            processInput.write(data.getBytes(StandardCharsets.UTF_8));
            processInput.flush();
        }

        void resize(int cols, int rows) {
            if (!closed && process != null) {
                try {
                    process.setWinSize(new WinSize(Math.max(cols, 2), Math.max(rows, 1)));
                } catch (Exception ignored) {
                }
            }
        }

        void close() {
            closed = true;
            try {
                if (processInput != null) {
                    processInput.close();
                }
            } catch (Exception ignored) {
            }
            if (process != null && process.isAlive()) {
                process.destroy();
                try {
                    if (!process.waitFor(1500, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                        process.destroyForcibly();
                    }
                } catch (Exception ignored) {
                    process.destroyForcibly();
                }
            }
            cleanupSshConfig();
        }

        private void cleanupSshConfig() {
            if (sshConfigDir == null) {
                return;
            }
            try {
                Files.walk(sshConfigDir)
                        .sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (Exception ignored) {
                            }
                        });
            } catch (Exception ignored) {
            } finally {
                sshConfigDir = null;
            }
        }

        private class OutputPump implements Runnable {
            private final InputStream output;

            OutputPump(InputStream output) {
                this.output = output;
            }

            public void run() {
                byte[] buffer = new byte[8192];
                try {
                    int len;
                    while (!closed && (len = output.read(buffer)) != -1) {
                        sendOutput(webSession, new String(buffer, 0, len, StandardCharsets.UTF_8));
                    }
                } catch (Exception e) {
                    if (!closed) {
                        sendError(webSession, e.getMessage());
                    }
                } finally {
                    close();
                    sendStatus(webSession, "DISCONNECTED", "로컬 AI CLI가 종료되었습니다.");
                }
            }
        }
    }
}
