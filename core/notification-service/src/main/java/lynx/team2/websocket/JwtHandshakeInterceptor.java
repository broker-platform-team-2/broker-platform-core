package lynx.team2.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtTokenParser tokenParser;

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        String token = extractToken(request);
        if (token == null) {
            reject(response, "Missing token");
            return false;
        }
        try {
            Long userId = tokenParser.extractUserId(token);
            attributes.put(NotificationWebSocketHandler.USER_ID_ATTR, userId);
            return true;
        } catch (Exception e) {
            log.warn("WS handshake rejected: {}", e.getMessage());
            reject(response, "Invalid token");
            return false;
        }
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception
    ) {}

    private String extractToken(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            String token = servletRequest.getServletRequest().getParameter("token");
            if (token != null && !token.isBlank()) return token;
        }
        var auth = request.getHeaders().getFirst("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7);
        }
        return null;
    }

    private void reject(ServerHttpResponse response, String reason) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        if (response instanceof ServletServerHttpResponse servletResponse) {
            servletResponse.getServletResponse().setHeader("X-Reject-Reason", reason);
        }
    }
}
