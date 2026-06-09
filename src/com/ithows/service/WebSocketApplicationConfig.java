package com.ithows.service;

import java.util.HashSet;
import java.util.Set;
import javax.websocket.Endpoint;
import javax.websocket.server.ServerApplicationConfig;
import javax.websocket.server.ServerEndpointConfig;

public class WebSocketApplicationConfig implements ServerApplicationConfig {

    public Set<ServerEndpointConfig> getEndpointConfigs(Set<Class<? extends Endpoint>> endpointClasses) {
        return Set.of();
    }

    public Set<Class<?>> getAnnotatedEndpointClasses(Set<Class<?>> scanned) {
        Set<Class<?>> endpoints = new HashSet<>();
        endpoints.add(SshTerminalWebSocket.class);
        endpoints.add(LocalAgentWebSocket.class);
        return endpoints;
    }
}
