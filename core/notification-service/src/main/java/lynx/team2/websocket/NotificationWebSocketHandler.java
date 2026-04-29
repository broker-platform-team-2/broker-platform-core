package lynx.team2.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    public static final String USER_ID_ATTR = "userId";

    private final SessionRegistry sessionRegistry;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = (Long) session.getAttributes().get(USER_ID_ATTR);
        if (userId == null) {
            log.warn("WebSocket session {} has no userId; closing", session.getId());
            try {
                session.close(CloseStatus.POLICY_VIOLATION);
            } catch (Exception e) {
                log.warn("Failed to close session", e);
            }
            return;
        }
        sessionRegistry.register(userId, session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = (Long) session.getAttributes().get(USER_ID_ATTR);
        if (userId != null) {
            sessionRegistry.unregister(userId, session);
        }
    }
}
