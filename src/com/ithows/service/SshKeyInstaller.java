package com.ithows.service;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyPair;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class SshKeyInstaller {

    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int COMMAND_TIMEOUT_MS = 20000;

    public JSONObject install(String host, int port, String username, String password, String privateKey) throws Exception {
        if (password == null || password.length() == 0) {
            throw new IllegalArgumentException("SSH key 설치에는 PW가 필요합니다.");
        }

        KeyMaterial key = privateKey == null || privateKey.trim().length() == 0
                ? generateKey(username, host)
                : publicKeyFromPrivateKey(privateKey);
        installPublicKey(host, port, username, password, key.publicKey);

        JSONObject out = new JSONObject();
        out.put("privateKey", key.privateKey);
        out.put("publicKey", key.publicKey);
        return out;
    }

    private KeyMaterial generateKey(String username, String host) throws Exception {
        JSch jsch = new JSch();
        KeyPair keyPair = KeyPair.genKeyPair(jsch, KeyPair.RSA, 4096);
        try {
            ByteArrayOutputStream privateOut = new ByteArrayOutputStream();
            ByteArrayOutputStream publicOut = new ByteArrayOutputStream();
            keyPair.writePrivateKey(privateOut);
            keyPair.writePublicKey(publicOut, "webterm-" + username + "@" + host);
            return new KeyMaterial(
                    privateOut.toString(StandardCharsets.UTF_8),
                    publicOut.toString(StandardCharsets.UTF_8).trim()
            );
        } finally {
            keyPair.dispose();
        }
    }

    private KeyMaterial publicKeyFromPrivateKey(String privateKey) throws Exception {
        JSch jsch = new JSch();
        KeyPair keyPair = KeyPair.load(jsch, privateKey.getBytes(StandardCharsets.UTF_8), null);
        try {
            ByteArrayOutputStream publicOut = new ByteArrayOutputStream();
            keyPair.writePublicKey(publicOut, "webterm-key");
            return new KeyMaterial(privateKey, publicOut.toString(StandardCharsets.UTF_8).trim());
        } finally {
            keyPair.dispose();
        }
    }

    private void installPublicKey(String host, int port, String username, String password, String publicKey) throws Exception {
        JSch jsch = new JSch();
        com.jcraft.jsch.Session session = jsch.getSession(username, host, port);
        session.setPassword(password);

        Properties config = new Properties();
        config.put("StrictHostKeyChecking", "no");
        config.put("PreferredAuthentications", "password,keyboard-interactive");
        session.setConfig(config);
        session.connect(CONNECT_TIMEOUT_MS);

        ChannelExec channel = null;
        try {
            String command = "umask 077; mkdir -p ~/.ssh"
                    + " && touch ~/.ssh/authorized_keys"
                    + " && (grep -qxF " + shellQuote(publicKey) + " ~/.ssh/authorized_keys"
                    + " || printf '%s\\n' " + shellQuote(publicKey) + " >> ~/.ssh/authorized_keys)"
                    + " && chmod 700 ~/.ssh"
                    + " && chmod 600 ~/.ssh/authorized_keys";
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            channel.setInputStream(null);
            channel.connect(CONNECT_TIMEOUT_MS);

            long deadline = System.currentTimeMillis() + COMMAND_TIMEOUT_MS;
            while (!channel.isClosed() && System.currentTimeMillis() < deadline) {
                Thread.sleep(100);
            }
            if (!channel.isClosed()) {
                throw new IllegalStateException("SSH key 설치 명령 시간이 초과되었습니다.");
            }
            if (channel.getExitStatus() != 0) {
                throw new IllegalStateException("SSH key 설치 명령 실패: exit " + channel.getExitStatus());
            }
        } finally {
            if (channel != null) {
                channel.disconnect();
            }
            session.disconnect();
        }
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private static class KeyMaterial {
        private final String privateKey;
        private final String publicKey;

        KeyMaterial(String privateKey, String publicKey) {
            this.privateKey = privateKey;
            this.publicKey = publicKey;
        }
    }
}
