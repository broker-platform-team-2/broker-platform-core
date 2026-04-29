package lynx.team2.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lynx.team2.client.NotificationServiceClient;
import lynx.team2.dto.NotificationMessage;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExchangeMessageDispatcher {

    private final ObjectMapper objectMapper;
    private final NotificationServiceClient notificationClient;
    private final OrderUpdateProcessor orderUpdateProcessor;

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
                case "ORDER_ACK", "ORDER_REJECTED" -> log.debug("Order ack/reject: {}", payload);
                default -> log.warn("Unknown exchange message type: {}", type);
            }
        } catch (Exception e) {
            log.error("Failed to dispatch exchange message: {}", rawMessage, e);
        }
    }

    private void handlePriceUpdate(JsonNode payload) {
        notificationClient.broadcast(new NotificationMessage(
                "PRICE_UPDATE",
                objectMapper.convertValue(payload, Map.class)
        ));
    }

    private void handleMarketEvent(JsonNode payload) {
        notificationClient.broadcast(new NotificationMessage(
                "MARKET_EVENT",
                objectMapper.convertValue(payload, Map.class)
        ));
    }

    private void handleOrderBookUpdate(JsonNode payload) {
        notificationClient.broadcast(new NotificationMessage(
                "ORDER_BOOK_UPDATE",
                objectMapper.convertValue(payload, Map.class)
        ));
    }
}
