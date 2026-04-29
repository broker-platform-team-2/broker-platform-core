package lynx.team2.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Bot-side auth filter.
 *
 * Today this validates the same JWTs as the user-gateway so bots that obtain a
 * token via /users/login can reach the trading endpoints. When the bot
 * registration / API-key design is finalized (see Authentication Model section
 * 2 of stock_exchange_spec.docx), swap this implementation for one that
 * validates the bot's API key + secret and propagates X-Bot-Id instead.
 */
@Component
public class BotAuthenticationFilter implements GlobalFilter, Ordered {

    private static final List<String> PUBLIC_PATHS = List.of(
            "/bots/register",
            "/bots/login",
            "/users/register",
            "/users/login"
    );

    private final SecretKey key;

    public BotAuthenticationFilter(@Value("${jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if (PUBLIC_PATHS.stream().anyMatch(path::equals)) {
            return chain.filter(exchange);
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return reject(exchange, "Missing or invalid Authorization header");
        }

        String token = authHeader.substring("Bearer ".length()).trim();
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String botId = claims.get("userId", String.class);
            ServerHttpRequest mutated = request.mutate()
                    .header("X-Bot-Id", botId == null ? "" : botId)
                    .build();

            return chain.filter(exchange.mutate().request(mutated).build());
        } catch (JwtException | IllegalArgumentException e) {
            return reject(exchange, "Invalid or expired token");
        }
    }

    private Mono<Void> reject(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add(HttpHeaders.CONTENT_TYPE, "application/json");
        String body = "{\"error\":\"" + message + "\"}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
