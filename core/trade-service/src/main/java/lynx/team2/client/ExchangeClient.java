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
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class ExchangeClient {

    /** REST client for the exchange REST API (market data, order lookup, cancel) */
    private final RestClient exchangeRestClient;

    /** REST client for exchange-client-service (order placement via WebSocket) */
    private final RestClient exchangeClientRestClient;

    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String apiSecret;
    private final String exchangeBaseUrl;

    private volatile String platformId = "unknown";

    public ExchangeClient(
            @Qualifier("exchangeRestClient") RestClient exchangeRestClient,
            @Qualifier("exchangeClientRestClient") RestClient exchangeClientRestClient,
            ObjectMapper objectMapper,
            @Value("${exchange.api-key}") String apiKey,
            @Value("${exchange.api-secret}") String apiSecret,
            @Value("${exchange.base-url}") String exchangeBaseUrl) {
        this.exchangeRestClient = exchangeRestClient;
        this.exchangeClientRestClient = exchangeClientRestClient;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.exchangeBaseUrl = exchangeBaseUrl;
    }

    @PostConstruct
    public void resolvePlatformId() {
        for (int attempt = 1; attempt <= 5; attempt++) {
            try {
                String requestBody = objectMapper.writeValueAsString(
                        Map.of("api_key", apiKey, "api_secret", apiSecret));
                String verifyUrl = exchangeBaseUrl + "/internal/platforms/verify";
                String response = exchangeRestClient.post()
                        .uri(verifyUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestBody)
                        .retrieve()
                        .body(String.class);
                if (response != null) {
                    JsonNode root = objectMapper.readTree(response);
                    if (root.path("valid").asBoolean(false)) {
                        platformId = root.path("platform_id").asText("unknown");
                        log.info("Resolved exchange platform_id={} (attempt {})", platformId, attempt);
                        return;
                    }
                }
            } catch (Exception e) {
                log.warn("Attempt {}/5 to resolve platform_id failed: {}", attempt, e.getMessage());
                if (attempt < 5) {
                    try { Thread.sleep(2000L * attempt); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
        log.warn("Could not resolve platform_id after 5 attempts; will retry on first order placement");
    }

    private String tryResolvePlatformId() {
        try {
            String requestBody = objectMapper.writeValueAsString(
                    Map.of("api_key", apiKey, "api_secret", apiSecret));
            String verifyUrl = exchangeBaseUrl + "/internal/platforms/verify";
            String response = exchangeRestClient.post()
                    .uri(verifyUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
            if (response != null) {
                JsonNode root = objectMapper.readTree(response);
                if (root.path("valid").asBoolean(false)) {
                    String resolved = root.path("platform_id").asText("unknown");
                    platformId = resolved;
                    log.info("Lazily resolved exchange platform_id={}", resolved);
                    return resolved;
                }
            }
        } catch (Exception e) {
            log.warn("Lazy platform_id resolution failed: {}", e.getMessage());
        }
        return "unknown";
    }

    /**
     * Places an order by forwarding it to exchange-client-service,
     * which sends a PLACE_ORDER message over the live exchange WebSocket
     * and waits for ORDER_ACK / ORDER_REJECTED before returning.
     */
    public ExchangeOrder placeOrder(Long platformUserId, PlaceOrderRequest request) {
        String orderId = UUID.randomUUID().toString();
        String resolvedPlatformId = "unknown".equals(platformId) ? tryResolvePlatformId() : platformId;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId",         orderId);
        payload.put("platformUserId",  String.valueOf(platformUserId));
        payload.put("instrumentType",  request.instrumentType().name());
        payload.put("instrumentId",    request.instrumentId());
        payload.put("orderType",       request.orderType().name());
        payload.put("side",            request.side().name());
        payload.put("quantity",        request.quantity());
        if (request.limitPrice() != null) payload.put("limitPrice", request.limitPrice());
        if (request.expiresAt()  != null) payload.put("expiresAt",  request.expiresAt().toString());

        try {
            exchangeClientRestClient.post()
                    .uri("/internal/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            throw new RuntimeException("Failed to place order via exchange WebSocket: " + e.getMessage(), e);
        }

        return new ExchangeOrder(
                orderId, resolvedPlatformId, String.valueOf(platformUserId),
                request.instrumentType().name(), request.instrumentId(),
                request.orderType().name(), request.side().name(),
                request.quantity(), request.limitPrice(),
                "PENDING", 0, null, null,
                LocalDateTime.now(), LocalDateTime.now(), request.expiresAt()
        );
    }

    public ExchangeOrder getOrder(String orderId) {
        return exchangeRestClient.get()
                .uri("/orders/{id}", orderId)
                .retrieve()
                .body(ExchangeOrder.class);
    }

    public void cancelOrder(String orderId) {
        exchangeRestClient.delete()
                .uri("/orders/{id}", orderId)
                .retrieve()
                .toBodilessEntity();
    }

    public StockSnapshot getStock(String ticker) {
        return exchangeRestClient.get()
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
