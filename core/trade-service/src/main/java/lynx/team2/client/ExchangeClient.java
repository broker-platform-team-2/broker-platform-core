package lynx.team2.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import lynx.team2.dto.PlaceOrderRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class ExchangeClient {

    private static final String ORDERS_TOPIC = "orders.requests";

    private final RestClient client;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String apiSecret;
    private final String exchangeBaseUrl;

    private volatile String platformId = "unknown";

    public ExchangeClient(@Qualifier("exchangeRestClient") RestClient client,
                          KafkaTemplate<String, String> kafkaTemplate,
                          ObjectMapper objectMapper,
                          @Value("${exchange.api-key}") String apiKey,
                          @Value("${exchange.api-secret}") String apiSecret,
                          @Value("${exchange.base-url}") String exchangeBaseUrl) {
        this.client = client;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.exchangeBaseUrl = exchangeBaseUrl;
    }

    @PostConstruct
    public void resolvePlatformId() {
        try {
            String requestBody = objectMapper.writeValueAsString(
                    Map.of("api_key", apiKey, "api_secret", apiSecret));
            // Use the full absolute URL so Spring bypasses base-URL path resolution
            String verifyUrl = exchangeBaseUrl + "/internal/platforms/verify";
            String response = client.post()
                    .uri(verifyUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
            if (response != null) {
                JsonNode root = objectMapper.readTree(response);
                if (root.path("valid").asBoolean(false)) {
                    platformId = root.path("platform_id").asText("unknown");
                    log.info("Resolved exchange platform_id: {}", platformId);
                }
            }
        } catch (Exception e) {
            log.warn("Could not resolve platform_id from exchange ({}); using 'unknown'", e.getMessage());
        }
    }

    public ExchangeOrder placeOrder(Long platformUserId, PlaceOrderRequest request) {
        String orderId = UUID.randomUUID().toString();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("order_id", orderId);
        payload.put("platform_id", platformId);
        payload.put("platform_user_id", String.valueOf(platformUserId));
        payload.put("instrument_type", request.instrumentType().name());
        payload.put("instrument_id", request.instrumentId());
        payload.put("order_type", request.orderType().name());
        payload.put("side", request.side().name());
        payload.put("quantity", request.quantity());
        if (request.limitPrice() != null) payload.put("limit_price", request.limitPrice());
        if (request.expiresAt() != null)  payload.put("expires_at", request.expiresAt().toString());

        try {
            String json = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(ORDERS_TOPIC, orderId, json).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Failed to publish order to exchange: " + e.getMessage(), e);
        }

        // Return a synthetic acknowledgment — the order is processed asynchronously
        // by the order-book-engine. Fills arrive later via the exchange-client-service.
        return new ExchangeOrder(
                orderId, platformId, String.valueOf(platformUserId),
                request.instrumentType().name(), request.instrumentId(),
                request.orderType().name(), request.side().name(),
                request.quantity(), request.limitPrice(),
                "PENDING", 0, null, null,
                LocalDateTime.now(), LocalDateTime.now(), request.expiresAt()
        );
    }

    public ExchangeOrder getOrder(String orderId) {
        return client.get()
                .uri("/orders/{id}", orderId)
                .retrieve()
                .body(ExchangeOrder.class);
    }

    public void cancelOrder(String orderId) {
        client.delete()
                .uri("/orders/{id}", orderId)
                .retrieve()
                .toBodilessEntity();
    }

    public StockSnapshot getStock(String ticker) {
        return client.get()
                .uri("/market/stocks/{ticker}", ticker)
                .retrieve()
                .body(StockSnapshot.class);
    }


    public record ExchangeOrderRequest(
            @JsonProperty("platform_user_id") String platformUserId,
            @JsonProperty("instrument_type") String instrumentType,
            @JsonProperty("instrument_id") String instrumentId,
            @JsonProperty("order_type") String orderType,
            String side,
            Integer quantity,
            @JsonProperty("limit_price") BigDecimal limitPrice,
            @JsonProperty("expires_at") LocalDateTime expiresAt
    ) {}

    public record ExchangeOrder(
            @JsonProperty("order_id") String orderId,
            @JsonProperty("platform_id") String platformId,
            @JsonProperty("platform_user_id") String platformUserId,
            @JsonProperty("instrument_type") String instrumentType,
            @JsonProperty("instrument_id") String instrumentId,
            @JsonProperty("order_type") String orderType,
            String side,
            Integer quantity,
            @JsonProperty("limit_price") BigDecimal limitPrice,
            String status,
            @JsonProperty("filled_quantity") Integer filledQuantity,
            @JsonProperty("average_fill_price") BigDecimal averageFillPrice,
            @JsonProperty("exchange_fee") BigDecimal exchangeFee,
            @JsonProperty("created_at") LocalDateTime createdAt,
            @JsonProperty("updated_at") LocalDateTime updatedAt,
            @JsonProperty("expires_at") LocalDateTime expiresAt
    ) {}

    public record StockSnapshot(
            String ticker,
            String name,
            @JsonProperty("current_price") BigDecimal currentPrice
    ) {}
}
