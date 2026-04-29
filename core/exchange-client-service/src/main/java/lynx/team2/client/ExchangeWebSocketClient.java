package lynx.team2.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lynx.team2.service.ExchangeMessageDispatcher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExchangeWebSocketClient {

    private final ExchangeMessageDispatcher dispatcher;
    private final ObjectMapper objectMapper;

    @Value("${exchange.ws-url}")
    private String wsUrl;

    @Value("${exchange.api-key}")
    private String apiKey;

    @Value("${exchange.api-secret}")
    private String apiSecret;

    private final AtomicReference<WebSocketSession> session = new AtomicReference<>();
    private final WebSocketClient client = new StandardWebSocketClient();

    @PostConstruct
    public void start() {
        connect();
    }

    @Scheduled(fixedDelayString = "${exchange.reconnect-delay-seconds:5}000")
    public void ensureConnected() {
        WebSocketSession current = session.get();
        if (current == null || !current.isOpen()) {
            connect();
        }
    }

    private void connect() {
        try {
            URI uri = URI.create(wsUrl + "?api_key=" + apiKey + "&api_secret=" + apiSecret);
            log.info("Connecting to exchange WebSocket: {}", uri);

            client.execute(new TextWebSocketHandler() {
                @Override
                public void afterConnectionEstablished(WebSocketSession ws) throws Exception {
                    session.set(ws);
                    log.info("Connected to exchange WebSocket");
                    subscribe(ws, "PRICE_FEED", null);
                    subscribe(ws, "ORDER_UPDATES", null);
                    subscribe(ws, "MARKET_EVENTS", null);
                }

                @Override
                protected void handleTextMessage(WebSocketSession ws, TextMessage message) {
                    dispatcher.dispatch(message.getPayload());
                }

                @Override
                public void afterConnectionClosed(WebSocketSession ws, CloseStatus status) {
                    log.warn("Exchange WebSocket closed: {}", status);
                    session.set(null);
                }

                @Override
                public void handleTransportError(WebSocketSession ws, Throwable exception) {
                    log.error("Exchange WebSocket transport error", exception);
                }
            }, uri.toString());
        } catch (Exception e) {
            log.error("Failed to connect to exchange WebSocket; will retry", e);
        }
    }

    private void subscribe(WebSocketSession ws, String channel, List<String> tickers) throws Exception {
        Map<String, Object> payload = tickers != null
                ? Map.of("channel", channel, "tickers", tickers)
                : Map.of("channel", channel);
        Map<String, Object> message = Map.of(
                "type", "SUBSCRIBE",
                "payload", payload
        );
        ws.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
    }

    public void send(String message) throws Exception {
        WebSocketSession current = session.get();
        if (current == null || !current.isOpen()) {
            throw new IllegalStateException("Exchange WebSocket is not connected");
        }
        current.sendMessage(new TextMessage(message));
    }
}
