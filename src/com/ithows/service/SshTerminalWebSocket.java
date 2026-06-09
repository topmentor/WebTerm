package com.ithows.service;

import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import javax.websocket.CloseReason;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

@ServerEndpoint(value = "/ssh-terminal", configurator = HttpSessionConfigurator.class)
public class SshTerminalWebSocket {

    private static final String CONNECTION_KEY = "sshConnection";
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int MAX_TEXT_SIZE = 1024 * 1024;

    @OnOpen
    public void onOpen(Session webSession) {
        webSession.setMaxIdleTimeout(0);
        webSession.setMaxTextMessageBufferSize(MAX_TEXT_SIZE);
        sendStatus(webSession, "READY", "SSH 접속 정보를 입력하세요.");
    }

    @OnMessage
    public void onMessage(String message, Session webSession) {
        try {
            JSONObject json = new JSONObject(message);
            String type = json.optString("type", "");

            if ("connect".equals(type)) {
                connect(json, webSession);
            } else if ("input".equals(type)) {
                SshConnection connection = getConnection(webSession);
                if (connection != null) {
                    connection.write(json.optString("data", ""));
                }
            } else if ("resize".equals(type)) {
                SshConnection connection = getConnection(webSession);
                if (connection != null) {
                    connection.resize(json.optInt("cols", 80), json.optInt("rows", 24));
                }
            } else if ("disconnect".equals(type)) {
                closeConnection(webSession);
                sendStatus(webSession, "DISCONNECTED", "SSH 연결을 종료했습니다.");
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

        String host = json.optString("host", "").trim();
        int port = json.optInt("port", 22);
        String username = json.optString("username", "").trim();
        String password = json.optString("password", "");
        String privateKey = json.optString("privateKey", "");
        String privateKeyPassphrase = json.optString("privateKeyPassphrase", "");
        String initialCommand = json.optString("initialCommand", "").trim();
        int cols = json.optInt("cols", 80);
        int rows = json.optInt("rows", 24);

        validate(host, port, username, password, privateKey);
        sendStatus(webSession, "CONNECTING", "웹서버에서 " + host + ":" + port + " 접속 확인 중...");
        verifyTcpReachable(host, port);
        sendStatus(webSession, "CONNECTING", "웹서버에서 " + host + ":" + port + " SSH 인증 중...");

        SshConnection connection = new SshConnection(webSession);
        connection.connect(host, port, username, password, privateKey, privateKeyPassphrase, cols, rows);
        webSession.getUserProperties().put(CONNECTION_KEY, connection);

        sendStatus(webSession, "CONNECTED", username + "@" + host + " 연결됨");
        if (initialCommand.length() > 0) {
            connection.write(initialCommand + "\n");
        }
    }

    private void validate(String host, int port, String username, String password, String privateKey) {
        if (host.length() == 0) {
            throw new IllegalArgumentException("주소를 입력하세요.");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("포트는 1-65535 사이여야 합니다.");
        }
        if (username.length() == 0) {
            throw new IllegalArgumentException("ID를 입력하세요.");
        }
        if (password.length() == 0 && privateKey.trim().length() == 0) {
            throw new IllegalArgumentException("PW 또는 SSH private key를 입력하세요.");
        }
    }

    private void verifyTcpReachable(String host, int port) throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "웹서버(Tomcat)에서 " + host + ":" + port
                            + " 로 TCP 연결할 수 없습니다. 방화벽, 보안그룹, VPN, 서버 outbound 정책 또는 대상 SSH 포트를 확인하세요. 원인: "
                            + e.getMessage(),
                    e
            );
        }
    }

    private SshConnection getConnection(Session webSession) {
        Object value = webSession.getUserProperties().get(CONNECTION_KEY);
        if (value instanceof SshConnection) {
            return (SshConnection) value;
        }
        return null;
    }

    private void closeConnection(Session webSession) {
        SshConnection connection = getConnection(webSession);
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

    private static class SshConnection {
        private final Session webSession;
        private com.jcraft.jsch.Session sshSession;
        private ChannelShell channel;
        private OutputStream sshInput;
        private Thread readerThread;
        private volatile boolean closed;

        SshConnection(Session webSession) {
            this.webSession = webSession;
        }

        void connect(String host, int port, String username, String password,
                     String privateKey, String privateKeyPassphrase,
                     int cols, int rows) throws Exception {
            JSch jsch = new JSch();
            if (privateKey != null && privateKey.trim().length() > 0) {
                byte[] keyBytes = privateKey.getBytes(StandardCharsets.UTF_8);
                byte[] passphraseBytes = null;
                if (privateKeyPassphrase != null && privateKeyPassphrase.length() > 0) {
                    passphraseBytes = privateKeyPassphrase.getBytes(StandardCharsets.UTF_8);
                }
                jsch.addIdentity(username + "@" + host, keyBytes, null, passphraseBytes);
            }
            sshSession = jsch.getSession(username, host, port);
            if (password != null && password.length() > 0) {
                sshSession.setPassword(password);
            }

            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            config.put("PreferredAuthentications", "publickey,password,keyboard-interactive");
            sshSession.setConfig(config);
            sshSession.connect(CONNECT_TIMEOUT_MS);

            channel = (ChannelShell) sshSession.openChannel("shell");
            channel.setPtyType("xterm-256color");
            channel.setPtySize(Math.max(cols, 2), Math.max(rows, 1), 0, 0);

            InputStream sshOutput = channel.getInputStream();
            sshInput = channel.getOutputStream();
            channel.connect(CONNECT_TIMEOUT_MS);

            readerThread = new Thread(new OutputPump(sshOutput), "ssh-terminal-output");
            readerThread.setDaemon(true);
            readerThread.start();
        }

        void write(String data) throws Exception {
            if (closed || sshInput == null) {
                return;
            }
            sshInput.write(data.getBytes(StandardCharsets.UTF_8));
            sshInput.flush();
        }

        void resize(int cols, int rows) {
            if (!closed && channel != null && channel.isConnected()) {
                channel.setPtySize(Math.max(cols, 2), Math.max(rows, 1), 0, 0);
            }
        }

        void close() {
            closed = true;
            try {
                if (sshInput != null) {
                    sshInput.close();
                }
            } catch (Exception ignored) {
            }
            if (channel != null) {
                channel.disconnect();
            }
            if (sshSession != null) {
                sshSession.disconnect();
            }
        }

        private class OutputPump implements Runnable {
            private final InputStream sshOutput;

            OutputPump(InputStream sshOutput) {
                this.sshOutput = sshOutput;
            }

            public void run() {
                byte[] buffer = new byte[8192];
                try {
                    int len;
                    while (!closed && (len = sshOutput.read(buffer)) != -1) {
                        sendOutput(webSession, new String(buffer, 0, len, StandardCharsets.UTF_8));
                    }
                } catch (Exception e) {
                    if (!closed) {
                        sendError(webSession, e.getMessage());
                    }
                } finally {
                    close();
                    sendStatus(webSession, "DISCONNECTED", "SSH 연결이 종료되었습니다.");
                }
            }
        }
    }
}
