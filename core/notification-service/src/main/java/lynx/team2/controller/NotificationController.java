package lynx.team2.controller;

import lombok.RequiredArgsConstructor;
import lynx.team2.dto.NotificationMessage;
import lynx.team2.websocket.SessionRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * REST endpoints used by exchange-client-service to push events into the
 * notification fan-out. End users do NOT call these directly — they connect
 * via WebSocket at /ws?token=...
 */
@RestController
@RequiredArgsConstructor
public class NotificationController {

    private final SessionRegistry sessionRegistry;

    @Value("${internal.token}")
    private String internalToken;

    @PostMapping("/notify/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Map<String, Object> notifyUser(
            @RequestHeader("X-Internal-Token") String token,
            @PathVariable Long userId,
            @RequestBody NotificationMessage message
    ) {
        verifyInternalToken(token);
        boolean delivered = sessionRegistry.sendToUser(userId, message);
        return Map.of("delivered", delivered);
    }

    @PostMapping("/broadcast")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void broadcast(
            @RequestHeader("X-Internal-Token") String token,
            @RequestBody NotificationMessage message) {
        verifyInternalToken(token);
        sessionRegistry.broadcast(message);
    }

    private void verifyInternalToken(String token) {
        if (!internalToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
    }
}
