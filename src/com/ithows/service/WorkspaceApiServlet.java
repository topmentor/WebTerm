package com.ithows.service;

import org.json.JSONObject;
import org.json.JSONArray;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import java.util.Vector;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.SftpATTRS;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;

public class WorkspaceApiServlet extends HttpServlet {

    private final WorkspaceStore store = new WorkspaceStore();
    private final SshKeyInstaller sshKeyInstaller = new SshKeyInstaller();
    private static final int SFTP_TIMEOUT_MS = 15000;
    private static final int BUFFER_SIZE = 64 * 1024;

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        handle(request, response);
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        handle(request, response);
    }

    private void handle(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            String action = action(request);
            if ("listServers".equals(action)) {
                JSONObject out = ok();
                out.put("servers", store.listServers());
                writeJson(response, out);
            } else if ("saveServer".equals(action)) {
                String host = param(request, "host").trim();
                int port = intParam(request, "port", 22);
                String username = param(request, "username").trim();
                validateServer(host, port, username);
                JSONObject out = ok();
                out.put("server", store.saveServer(
                        host,
                        port,
                        username,
                        param(request, "password"),
                        param(request, "privateKey"),
                        param(request, "privateKeyPassphrase")
                ));
                writeJson(response, out);
            } else if ("deleteServer".equals(action)) {
                JSONObject out = ok();
                long id = longParam(request, "id", 0);
                out.put("deleted", id > 0 && store.deleteServer(id));
                writeJson(response, out);
            } else if ("installSshKey".equals(action)) {
                String host = param(request, "host").trim();
                int port = intParam(request, "port", 22);
                String username = param(request, "username").trim();
                validateServer(host, port, username);
                JSONObject installed = sshKeyInstaller.install(
                        host,
                        port,
                        username,
                        param(request, "password"),
                        param(request, "privateKey")
                );
                JSONObject saved = store.saveServerPrivateKey(
                        host,
                        port,
                        username,
                        installed.getString("privateKey"),
                        ""
                );
                JSONObject out = ok();
                out.put("server", saved);
                out.put("publicKey", installed.getString("publicKey"));
                writeJson(response, out);
            } else if ("listCommands".equals(action)) {
                JSONObject out = ok();
                out.put("commands", store.listCommands());
                writeJson(response, out);
            } else if ("saveCommand".equals(action)) {
                String command = param(request, "command").trim();
                if (command.length() == 0) {
                    throw new IllegalArgumentException("command is required");
                }
                JSONObject out = ok();
                out.put("command", store.saveCommand(command));
                writeJson(response, out);
            } else if ("deleteCommand".equals(action)) {
                JSONObject out = ok();
                long id = longParam(request, "id", 0);
                out.put("deleted", id > 0 && store.deleteCommand(id));
                writeJson(response, out);
            } else if ("getSettings".equals(action)) {
                JSONObject out = ok();
                out.put("settings", store.getSettings());
                writeJson(response, out);
            } else if ("saveSettings".equals(action)) {
                JSONObject out = ok();
                out.put("settings", store.saveSettings(
                        param(request, "terminalFontFamily"),
                        intParam(request, "terminalFontSize", 14)
                ));
                writeJson(response, out);
            } else if ("exportServers".equals(action)) {
                response.setHeader("Content-Disposition", "attachment; filename=\"ssh-servers.json\"");
                writeJson(response, store.exportServersJson());
            } else if ("sftpList".equals(action)) {
                SftpClient client = sftpClient(request);
                JSONObject out;
                try {
                    out = listRemote(client, param(request, "path"));
                    out.put("result", "OK");
                } finally {
                    client.close();
                }
                writeJson(response, out);
            } else if ("sftpMkdir".equals(action)) {
                SftpClient client = sftpClient(request);
                try {
                    client.channel.mkdir(param(request, "path"));
                } finally {
                    client.close();
                }
                writeJson(response, ok());
            } else if ("sftpDelete".equals(action)) {
                SftpClient client = sftpClient(request);
                try {
                    deleteRemote(client.channel, param(request, "path"), "true".equals(param(request, "dir")));
                } finally {
                    client.close();
                }
                writeJson(response, ok());
            } else if ("sftpRename".equals(action)) {
                SftpClient client = sftpClient(request);
                try {
                    client.channel.rename(param(request, "from"), param(request, "to"));
                } finally {
                    client.close();
                }
                writeJson(response, ok());
            } else if ("sftpDownload".equals(action)) {
                downloadRemote(request, response);
            } else if ("sftpUpload".equals(action)) {
                uploadRemote(request, response);
            } else {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                JSONObject out = new JSONObject();
                out.put("result", "ERROR");
                out.put("msg", "Unknown workspace API: " + action);
                writeJson(response, out);
            }
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JSONObject out = new JSONObject();
            out.put("result", "ERROR");
            out.put("msg", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            writeJson(response, out);
        }
    }

    private String action(HttpServletRequest request) {
        String path = request.getPathInfo();
        if (path == null || path.length() == 0 || "/".equals(path)) {
            String uri = request.getRequestURI();
            String prefix = request.getContextPath() + "/api/workspace/";
            path = uri.startsWith(prefix) ? uri.substring(prefix.length()) : uri;
        } else if (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.endsWith(".do")) {
            path = path.substring(0, path.length() - 3);
        }
        return path;
    }

    private String param(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        return value == null ? "" : value;
    }

    private int intParam(HttpServletRequest request, String name, int defaultValue) {
        try {
            return Integer.parseInt(param(request, name));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private long longParam(HttpServletRequest request, String name, long defaultValue) {
        try {
            return Long.parseLong(param(request, name));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private JSONObject ok() {
        JSONObject out = new JSONObject();
        out.put("result", "OK");
        return out;
    }

    private void validateServer(String host, int port, String username) {
        if (host.length() == 0) {
            throw new IllegalArgumentException("host is required");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be 1-65535");
        }
        if (username.length() == 0) {
            throw new IllegalArgumentException("username is required");
        }
    }

    private SftpClient sftpClient(HttpServletRequest request) throws Exception {
        String host = param(request, "host").trim();
        int port = intParam(request, "port", 22);
        String username = param(request, "username").trim();
        String password = param(request, "password");
        String privateKey = param(request, "privateKey");
        String privateKeyPassphrase = param(request, "privateKeyPassphrase");
        validateServer(host, port, username);
        if (password.length() == 0 && privateKey.trim().length() == 0) {
            throw new IllegalArgumentException("password or privateKey is required");
        }
        return connectSftp(host, port, username, password, privateKey, privateKeyPassphrase);
    }

    private SftpClient connectSftp(String host, int port, String username, String password,
                                   String privateKey, String privateKeyPassphrase) throws Exception {
        JSch jsch = new JSch();
        if (privateKey != null && privateKey.trim().length() > 0) {
            byte[] keyBytes = privateKey.getBytes(StandardCharsets.UTF_8);
            byte[] passphraseBytes = privateKeyPassphrase == null || privateKeyPassphrase.length() == 0
                    ? null : privateKeyPassphrase.getBytes(StandardCharsets.UTF_8);
            jsch.addIdentity(username + "@" + host + "-sftp", keyBytes, null, passphraseBytes);
        }
        com.jcraft.jsch.Session session = jsch.getSession(username, host, port);
        if (password != null && password.length() > 0) {
            session.setPassword(password);
        }
        Properties config = new Properties();
        config.put("StrictHostKeyChecking", "no");
        config.put("PreferredAuthentications", "publickey,password,keyboard-interactive");
        session.setConfig(config);
        session.connect(SFTP_TIMEOUT_MS);
        ChannelSftp channel = (ChannelSftp) session.openChannel("sftp");
        channel.connect(SFTP_TIMEOUT_MS);
        return new SftpClient(session, channel);
    }

    private JSONObject listRemote(SftpClient client, String rawPath) throws Exception {
        String path = rawPath == null || rawPath.trim().length() == 0 ? "." : rawPath.trim();
        client.channel.cd(path);
        String currentPath = client.channel.pwd();
        Vector entries = client.channel.ls(".");
        JSONArray files = new JSONArray();
        for (Object object : entries) {
            ChannelSftp.LsEntry entry = (ChannelSftp.LsEntry) object;
            String name = entry.getFilename();
            if (".".equals(name) || "..".equals(name)) {
                continue;
            }
            SftpATTRS attrs = entry.getAttrs();
            JSONObject file = new JSONObject();
            file.put("name", name);
            file.put("path", joinRemote(currentPath, name));
            file.put("dir", attrs.isDir());
            file.put("link", attrs.isLink());
            file.put("size", attrs.getSize());
            file.put("permissions", attrs.getPermissionsString());
            file.put("modified", attrs.getMTime() * 1000L);
            files.put(file);
        }
        JSONObject out = new JSONObject();
        out.put("path", currentPath);
        out.put("parent", parentRemote(currentPath));
        out.put("files", files);
        return out;
    }

    private void deleteRemote(ChannelSftp channel, String path, boolean dir) throws Exception {
        if (path == null || path.trim().length() == 0 || "/".equals(path.trim())) {
            throw new IllegalArgumentException("삭제할 경로가 올바르지 않습니다.");
        }
        if (dir) {
            channel.rmdir(path);
        } else {
            channel.rm(path);
        }
    }

    private void downloadRemote(HttpServletRequest request, HttpServletResponse response) throws Exception {
        SftpClient client = sftpClient(request);
        String path = param(request, "path");
        try {
            SftpATTRS attrs = client.channel.stat(path);
            if (attrs.isDir()) {
                throw new IllegalArgumentException("디렉토리는 다운로드할 수 없습니다.");
            }
            String filename = basename(path);
            response.setContentType("application/octet-stream");
            response.setHeader("Content-Length", String.valueOf(attrs.getSize()));
            response.setHeader("Content-Disposition", "attachment; filename=\"" + asciiFilename(filename)
                    + "\"; filename*=UTF-8''" + URLEncoder.encode(filename, "UTF-8").replace("+", "%20"));
            try (InputStream input = client.channel.get(path); OutputStream output = response.getOutputStream()) {
                copy(input, output);
            }
        } finally {
            client.close();
        }
    }

    private void uploadRemote(HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (!ServletFileUpload.isMultipartContent(request)) {
            throw new IllegalArgumentException("multipart/form-data request is required");
        }
        DiskFileItemFactory factory = new DiskFileItemFactory();
        ServletFileUpload upload = new ServletFileUpload(factory);
        upload.setHeaderEncoding("UTF-8");
        List items = upload.parseRequest(request);

        String host = multipartParam(items, "host").trim();
        int port = parseInt(multipartParam(items, "port"), 22);
        String username = multipartParam(items, "username").trim();
        String password = multipartParam(items, "password");
        String privateKey = multipartParam(items, "privateKey");
        String privateKeyPassphrase = multipartParam(items, "privateKeyPassphrase");
        String path = multipartParam(items, "path");
        validateServer(host, port, username);

        SftpClient client = connectSftp(host, port, username, password, privateKey, privateKeyPassphrase);
        try {
            for (Object object : items) {
                FileItem item = (FileItem) object;
                if (item.isFormField() || item.getSize() <= 0) {
                    continue;
                }
                String filename = basename(item.getName());
                if (filename.length() == 0) {
                    continue;
                }
                String remotePath = joinRemote(path, filename);
                try (InputStream input = item.getInputStream()) {
                    client.channel.put(input, remotePath, ChannelSftp.OVERWRITE);
                }
            }
        } finally {
            client.close();
        }
        writeJson(response, ok());
    }

    private String multipartParam(List items, String name) throws Exception {
        for (Object object : items) {
            FileItem item = (FileItem) object;
            if (item.isFormField() && name.equals(item.getFieldName())) {
                return item.getString("UTF-8");
            }
        }
        return "";
    }

    private int parseInt(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private String joinRemote(String dir, String name) {
        String left = dir == null || dir.trim().length() == 0 ? "." : dir.trim();
        String right = name == null ? "" : name.trim();
        if ("/".equals(left)) {
            return "/" + right;
        }
        return left.replaceAll("/+$", "") + "/" + right;
    }

    private String parentRemote(String path) {
        if (path == null || path.length() == 0 || "/".equals(path)) {
            return "/";
        }
        int idx = path.lastIndexOf('/');
        if (idx <= 0) {
            return "/";
        }
        return path.substring(0, idx);
    }

    private String basename(String path) {
        if (path == null) {
            return "";
        }
        String value = path.replace('\\', '/');
        int idx = value.lastIndexOf('/');
        return idx >= 0 ? value.substring(idx + 1) : value;
    }

    private String asciiFilename(String filename) {
        return filename.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int len;
        while ((len = input.read(buffer)) != -1) {
            output.write(buffer, 0, len);
        }
    }

    private static class SftpClient {
        private final com.jcraft.jsch.Session session;
        private final ChannelSftp channel;

        SftpClient(com.jcraft.jsch.Session session, ChannelSftp channel) {
            this.session = session;
            this.channel = channel;
        }

        void close() {
            if (channel != null) {
                channel.disconnect();
            }
            if (session != null) {
                session.disconnect();
            }
        }
    }

    private void writeJson(HttpServletResponse response, JSONObject json) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        PrintWriter writer = response.getWriter();
        writer.print(json.toString());
        writer.flush();
    }
}
