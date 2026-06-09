package com.ithows.service;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.websocket.server.ServerContainer;

public class WebSocketEndpointInitializer implements ServletContextListener {

    public void contextInitialized(ServletContextEvent event) {
        ServletContext context = event.getServletContext();
        Object value = context.getAttribute("javax.websocket.server.ServerContainer");
        if (!(value instanceof ServerContainer)) {
            context.setAttribute("webterm.websocket.registration", "ServerContainer is not available");
            context.log("WebSocket ServerContainer is not available. SSH terminal endpoints are disabled.");
            return;
        }

        ServerContainer container = (ServerContainer) value;
        register(context, container, SshTerminalWebSocket.class);
        register(context, container, LocalAgentWebSocket.class);
    }

    public void contextDestroyed(ServletContextEvent event) {
    }

    private void register(ServletContext context, ServerContainer container, Class<?> endpointClass) {
        try {
            container.addEndpoint(endpointClass);
            context.setAttribute("webterm.websocket." + endpointClass.getSimpleName(), "registered");
            context.log("Registered WebSocket endpoint: " + endpointClass.getName());
        } catch (Exception e) {
            String message = e.getMessage() == null ? "" : e.getMessage();
            if (message.toLowerCase().contains("duplicate") || message.toLowerCase().contains("already")) {
                context.setAttribute("webterm.websocket." + endpointClass.getSimpleName(), "already registered");
                context.log("WebSocket endpoint already registered: " + endpointClass.getName());
            } else {
                context.setAttribute("webterm.websocket." + endpointClass.getSimpleName(), "failed: " + message);
                context.log("Failed to register WebSocket endpoint: " + endpointClass.getName(), e);
            }
        }
    }
}
