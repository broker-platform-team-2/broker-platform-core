package lynx.team2.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class SecurityHeadersFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            ServerHttpResponse response = exchange.getResponse();
            response.getHeaders().set("Strict-Transport-Security", "max-age=63072000; includeSubDomains");
            response.getHeaders().set("X-Content-Type-Options", "nosniff");
            response.getHeaders().set("X-Frame-Options", "DENY");
            response.getHeaders().set("Referrer-Policy", "no-referrer");
            response.getHeaders().set("Content-Security-Policy", "default-src 'self'");
        }));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
