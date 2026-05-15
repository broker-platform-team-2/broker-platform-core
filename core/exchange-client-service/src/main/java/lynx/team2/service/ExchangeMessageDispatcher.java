package lynx.team2.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lynx.team2.client.NotificationServiceClient;
import lynx.team2.dto.NotificationMessage;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExchangeMessageDispatcher {

    private final ObjectMapper objectMapper;
    private final NotificationServiceClient notificationClient;
    private final OrderUpdateProcessor orderUpdateProcessor;

    // Counters for visibility — log on every Nth message to avoid flooding
    private final AtomicLong priceUpdateCount = new AtomicLong();
    private final AtomicLong marketEventCount = new AtomicLong();
    private static final long PRICE_LOG_EVERY = 50;

    public void dispatch(String rawMessage) {
        try {
            JsonNode root = objectMapper.readTree(rawMessage);
            String type = root.path("type").asText();
            JsonNode payload = root.path("payload");

            switch (type) {
                case "CONNECTED" -> log.info("Exchange connection confirmed: {}", payload);
                case "PRICE_UPDATE" -> handlePriceUpdate(payload);
                case "ORDER_UPDATE" -> orderUpdateProcessor.process(payload);
                case "MARKET_EVENT" -> handleMarketEvent(payload);
                case "ORDER_BOOK_UPDATE" -> handleOrderBookUpdate(payload);
                case "ORDER_ACK", "ORDER_REJECTED" -> log.info("Order {}: {}", type, payload);
                default -> log.warn("Unknown exchange message type='{}' raw={}", type, rawMessage);
            }
        } catch (Exception e) {
            log.error("Failed to dispatch exchange message: {}", rawMessage, e);
        }
    }

    private void handlePriceUpdate(JsonNode payload) {
        long n = priceUpdateCount.incrementAndGet();
        if (n <= 3 || n % PRICE_LOG_EVERY == 0) {
            log.info("PRICE_UPDATE #{} ticker={} price={}", n,
                    payload.path("ticker").asText(),
                    payload.path("price").asText());
        }
        try {
            notificationClient.broadcast(new NotificationMessage(
                    "PRICE_UPDATE",
                    objectMapper.convertValue(payload, Map.class)
            ));
        } catch (Exception e) {
            log.error("Failed to broadcast PRICE_UPDATE", e);
        }
    }

    private void handleMarketEvent(JsonNode payload) {
        long n = marketEventCount.incrementAndGet();
        log.info("MARKET_EVENT #{} type={} headline={}", n,
                payload.path("event_type").asText(),
                payload.path("headline").asText());
        try {
            notificationClient.broadcast(new NotificationMessage(
                    "MARKET_EVENT",
                    objectMapper.convertValue(payload, Map.class)
            ));
        } catch (Exception e) {
            log.error("Failed to broadcast MARKET_EVENT", e);
        }
    }

    private void handleOrderBookUpdate(JsonNode payload) {
        notificationClient.broadcast(new NotificationMessage(
                "ORDER_BOOK_UPDATE",
                objectMapper.convertValue(payload, Map.class)
        ));
    }
}
