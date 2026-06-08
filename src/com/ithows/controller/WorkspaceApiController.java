package com.ithows.controller;

import com.ithows.HttpUtil;
import com.ithows.base.ControllerClassInfo;
import com.ithows.base.ControllerMethodInfo;
import com.ithows.service.WorkspaceStore;
import org.json.JSONObject;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.PrintWriter;

@ControllerClassInfo(controllerPage = "/api/_api.jsp")
public class WorkspaceApiController {

    private final WorkspaceStore store = new WorkspaceStore();

    @ControllerMethodInfo(id = "/api/workspace/listServers.do")
    public String listServers(HttpSession session, HttpServletRequest request,
                              HttpServletResponse response, Object command) throws Exception {
        JSONObject out = ok();
        out.put("servers", store.listServers());
        writeJson(response, out);
        return "NO_PAGE";
    }

    @ControllerMethodInfo(id = "/api/workspace/saveServer.do")
    public String saveServer(HttpSession session, HttpServletRequest request,
                             HttpServletResponse response, Object command) throws Exception {
        String host = HttpUtil.getParameterString(request, "host", "").trim();
        int port = HttpUtil.getParameterInt(request, "port", 22);
        String username = HttpUtil.getParameterString(request, "username", "").trim();
        String password = HttpUtil.getParameterString(request, "password", "");
        validateServer(host, port, username);

        JSONObject out = ok();
        out.put("server", store.saveServer(host, port, username, password));
        writeJson(response, out);
        return "NO_PAGE";
    }

    @ControllerMethodInfo(id = "/api/workspace/deleteServer.do")
    public String deleteServer(HttpSession session, HttpServletRequest request,
                               HttpServletResponse response, Object command) throws Exception {
        long id = HttpUtil.getParameterLong(request, "id", 0);
        JSONObject out = ok();
        out.put("deleted", id > 0 && store.deleteServer(id));
        writeJson(response, out);
        return "NO_PAGE";
    }

    @ControllerMethodInfo(id = "/api/workspace/listCommands.do")
    public String listCommands(HttpSession session, HttpServletRequest request,
                               HttpServletResponse response, Object command) throws Exception {
        JSONObject out = ok();
        out.put("commands", store.listCommands());
        writeJson(response, out);
        return "NO_PAGE";
    }

    @ControllerMethodInfo(id = "/api/workspace/saveCommand.do")
    public String saveCommand(HttpSession session, HttpServletRequest request,
                              HttpServletResponse response, Object command) throws Exception {
        String commandText = HttpUtil.getParameterString(request, "command", "").trim();
        if (commandText.length() == 0) {
            throw new IllegalArgumentException("command is required");
        }
        JSONObject out = ok();
        out.put("command", store.saveCommand(commandText));
        writeJson(response, out);
        return "NO_PAGE";
    }

    @ControllerMethodInfo(id = "/api/workspace/deleteCommand.do")
    public String deleteCommand(HttpSession session, HttpServletRequest request,
                                HttpServletResponse response, Object command) throws Exception {
        long id = HttpUtil.getParameterLong(request, "id", 0);
        JSONObject out = ok();
        out.put("deleted", id > 0 && store.deleteCommand(id));
        writeJson(response, out);
        return "NO_PAGE";
    }

    @ControllerMethodInfo(id = "/api/workspace/getSettings.do")
    public String getSettings(HttpSession session, HttpServletRequest request,
                              HttpServletResponse response, Object command) throws Exception {
        JSONObject out = ok();
        out.put("settings", store.getSettings());
        writeJson(response, out);
        return "NO_PAGE";
    }

    @ControllerMethodInfo(id = "/api/workspace/saveSettings.do")
    public String saveSettings(HttpSession session, HttpServletRequest request,
                               HttpServletResponse response, Object command) throws Exception {
        String terminalFontFamily = HttpUtil.getParameterString(request, "terminalFontFamily", "");
        int terminalFontSize = HttpUtil.getParameterInt(request, "terminalFontSize", 14);
        JSONObject out = ok();
        out.put("settings", store.saveSettings(terminalFontFamily, terminalFontSize));
        writeJson(response, out);
        return "NO_PAGE";
    }

    @ControllerMethodInfo(id = "/api/workspace/exportServers.do")
    public String exportServers(HttpSession session, HttpServletRequest request,
                                HttpServletResponse response, Object command) throws Exception {
        response.setHeader("Content-Disposition", "attachment; filename=\"ssh-servers.json\"");
        writeJson(response, store.exportServersJson());
        return "NO_PAGE";
    }

    private static JSONObject ok() {
        JSONObject out = new JSONObject();
        out.put("result", "OK");
        return out;
    }

    private static void validateServer(String host, int port, String username) {
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

    private static void writeJson(HttpServletResponse response, JSONObject json) throws Exception {
        response.setContentType("application/json;charset=UTF-8");
        PrintWriter writer = response.getWriter();
        writer.print(json.toString());
        writer.flush();
    }
}
