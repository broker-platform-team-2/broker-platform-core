package lynx.team2.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lynx.team2.dto.NotificationMessage;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class SessionRegistry {

    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<Long, Set<WebSocketSession>> sessionsByUserId = new ConcurrentHashMap<>();

    public void register(Long userId, WebSocketSession session) {
        sessionsByUserId.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session);
        log.info("Registered session {} for user {}", session.getId(), userId);
    }

    public void unregister(Long userId, WebSocketSession session) {
        Set<WebSocketSession> sessions = sessionsByUserId.get(userId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                sessionsByUserId.remove(userId);
            }
        }
        log.info("Unregistered session {} for user {}", session.getId(), userId);
    }

    public boolean sendToUser(Long userId, NotificationMessage message) {
        Set<WebSocketSession> sessions = sessionsByUserId.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return false;
        }
        String json = serialize(message);
        sessions.forEach(s -> safeSend(s, json));
        return true;
    }

    public void broadcast(NotificationMessage message) {
        String json = serialize(message);
        sessionsByUserId.values().forEach(set -> set.forEach(s -> safeSend(s, json)));
    }

    private String serialize(NotificationMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize notification", e);
        }
    }

    private void safeSend(WebSocketSession session, String message) {
        if (!session.isOpen()) return;
        try {
            session.sendMessage(new TextMessage(message));
        } catch (IOException e) {
            log.warn("Failed to deliver message to session {}: {}", session.getId(), e.getMessage());
        }
    }
}
