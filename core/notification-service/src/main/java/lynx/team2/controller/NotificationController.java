package lynx.team2.controller;

import lombok.RequiredArgsConstructor;
import lynx.team2.dto.NotificationMessage;
import lynx.team2.websocket.SessionRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping("/notify/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Map<String, Object> notifyUser(
            @PathVariable Long userId,
            @RequestBody NotificationMessage message
    ) {
        boolean delivered = sessionRegistry.sendToUser(userId, message);
        return Map.of("delivered", delivered);
    }

    @PostMapping("/broadcast")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void broadcast(@RequestBody NotificationMessage message) {
        sessionRegistry.broadcast(message);
    }
}
