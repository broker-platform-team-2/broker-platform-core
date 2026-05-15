package lynx.team2.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import lynx.team2.service.ExchangeMessageDispatcher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
public class ExchangeWebSocketClient {

    private final ExchangeMessageDispatcher dispatcher;
    private final ObjectMapper objectMapper;
    private final RestClient exchangeRestClient;

    @Value("${exchange.ws-url}")
    private String wsUrl;

    @Value("${exchange.api-key}")
    private String apiKey;

    @Value("${exchange.api-secret}")
    private String apiSecret;

    /**
     * Comma-separated fallback ticker list. Used only if dynamic discovery via
     * GET /market/stocks fails or returns an empty catalog.
     */
    @Value("${exchange.price-feed.tickers:}")
    private String fallbackTickersCsv;

    private final AtomicReference<WebSocketSession> session = new AtomicReference<>();
    private final WebSocketClient client = new StandardWebSocketClient();

    public ExchangeWebSocketClient(ExchangeMessageDispatcher dispatcher,
                                   ObjectMapper objectMapper,
                                   @Qualifier("exchangeRestClient") RestClient exchangeRestClient) {
        this.dispatcher = dispatcher;
        this.objectMapper = objectMapper;
        this.exchangeRestClient = exchangeRestClient;
    }

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
            log.info("Connecting to exchange WebSocket: {}", wsUrl);

            client.execute(new TextWebSocketHandler() {
                @Override
                public void afterConnectionEstablished(WebSocketSession ws) throws Exception {
                    session.set(ws);
                    log.info("Connected to exchange WebSocket");
                    subscribe(ws, "ORDER_UPDATES", null);
                    subscribe(ws, "MARKET_EVENTS", null);

                    List<String> tickers = resolvePriceFeedTickers();
                    if (tickers.isEmpty()) {
                        log.warn("No tickers available for PRICE_FEED subscription; live prices will NOT be received");
                    } else {
                        subscribe(ws, "PRICE_FEED", tickers);
                        log.info("Subscribed to PRICE_FEED for {} tickers: {}", tickers.size(), tickers);
                    }
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

    /**
     * Try to discover tickers from the exchange REST catalog; fall back to the
     * configured comma-separated list if discovery fails or is empty.
     */
    private List<String> resolvePriceFeedTickers() {
        Set<String> tickers = new LinkedHashSet<>();
        try {
            List<Map<String, Object>> stocks = exchangeRestClient.get()
                    .uri("/market/stocks")
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            if (stocks != null) {
                for (Map<String, Object> stock : stocks) {
                    Object ticker = stock.get("ticker");
                    if (ticker != null) {
                        String normalized = ticker.toString().trim().toUpperCase();
                        if (!normalized.isEmpty()) {
                            tickers.add(normalized);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not discover stock catalog from exchange ({}); falling back to configured tickers", e.getMessage());
        }

        if (tickers.isEmpty() && fallbackTickersCsv != null && !fallbackTickersCsv.isBlank()) {
            Arrays.stream(fallbackTickersCsv.split(","))
                    .map(String::trim)
                    .map(String::toUpperCase)
                    .filter(s -> !s.isEmpty())
                    .forEach(tickers::add);
        }
        return new ArrayList<>(tickers);
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
