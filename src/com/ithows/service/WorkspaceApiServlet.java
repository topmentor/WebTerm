package com.ithows.service;

import org.json.JSONObject;

import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class WorkspaceApiServlet extends HttpServlet {

    private final WorkspaceStore store = new WorkspaceStore();
    private final SshKeyInstaller sshKeyInstaller = new SshKeyInstaller();

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

    private void writeJson(HttpServletResponse response, JSONObject json) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        PrintWriter writer = response.getWriter();
        writer.print(json.toString());
        writer.flush();
    }
}
