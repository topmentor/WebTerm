package com.ithows.service;

import org.json.JSONObject;

import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class WebSocketStatusServlet extends HttpServlet {

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ServletContext context = request.getServletContext();
        Object serverContainer = context.getAttribute("javax.websocket.server.ServerContainer");

        JSONObject out = new JSONObject();
        out.put("result", "OK");
        out.put("serverContainerAvailable", serverContainer != null);
        out.put("serverContainerClass", serverContainer == null ? "" : serverContainer.getClass().getName());
        out.put("sshRegistration", String.valueOf(context.getAttribute("webterm.websocket.SshTerminalWebSocket")));
        out.put("localAgentRegistration", String.valueOf(context.getAttribute("webterm.websocket.LocalAgentWebSocket")));
        out.put("registrationError", String.valueOf(context.getAttribute("webterm.websocket.registration")));
        out.put("sshEndpoint", request.getContextPath() + "/ssh-terminal");
        out.put("localAgentEndpoint", request.getContextPath() + "/local-agent-terminal");

        response.setContentType("application/json;charset=UTF-8");
        PrintWriter writer = response.getWriter();
        writer.print(out.toString());
        writer.flush();
    }
}
