package com.example.cortebe.domain.judge.controller;

import com.example.cortebe.domain.judge.service.JobManager;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class ExecutionSocket extends TextWebSocketHandler {
    private final JobManager manager;
    private final ObjectMapper mapper;
    private final Map<String, Connection> connections = new ConcurrentHashMap<>();

    private static final class Connection {
        final WebSocketSession session;
        final long created = System.currentTimeMillis();
        volatile JobManager.Channel channel;
        Connection(WebSocketSession session) {
            this.session = new ConcurrentWebSocketSessionDecorator(session, 2000, 65536);
        }
    }

    @Override
    public synchronized void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if (connections.size() >= 64) { session.close(CloseStatus.POLICY_VIOLATION); return; }
        session.setTextMessageSizeLimit(16384);
        connections.put(session.getId(), new Connection(session));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Connection connection = connections.get(session.getId());
        if (connection == null) { session.close(CloseStatus.POLICY_VIOLATION); return; }
        try {
            JsonNode json = mapper.readTree(message.getPayload());
            String type = json.path("type").asText();
            if (connection.channel == null) {
                if (!type.equals("auth")) throw new IllegalArgumentException();
                String[] path = session.getUri().getPath().split("/");
                Long id = Long.valueOf(path[3]);
                connection.channel = manager.connect(id, json.path("token").asText(), event -> {
                    try { connection.session.sendMessage(new TextMessage(mapper.writeValueAsString(event))); }
                    catch (Exception exception) {
                        if (connection.channel != null) connection.channel.cancel();
                        try { connection.session.close(CloseStatus.SERVER_ERROR); } catch (IOException ignored) { }
                    }
                });
                if (!connection.session.isOpen()) connection.channel.cancel();
            } else switch (type) {
                case "stdin" -> connection.channel.write(json.path("data").asText());
                case "eof" -> connection.channel.eof();
                case "cancel" -> connection.channel.cancel();
                default -> throw new IllegalArgumentException();
            }
        } catch (Exception exception) {
            if (connection.channel != null) connection.channel.cancel();
            session.close(CloseStatus.POLICY_VIOLATION);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Connection connection = connections.remove(session.getId());
        if (connection != null && connection.channel != null) connection.channel.cancel();
    }

    @Scheduled(fixedDelay = 5000)
    public void expireUnauthenticated() {
        connections.values().forEach(connection -> {
            long age = System.currentTimeMillis() - connection.created;
            if ((connection.channel == null && age > 10000) || age > 900000) {
                try { connection.session.close(CloseStatus.POLICY_VIOLATION); } catch (IOException ignored) { }
            }
        });
    }
}
