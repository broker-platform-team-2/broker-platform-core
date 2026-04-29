package lynx.team2.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import lynx.team2.dto.PlaceOrderRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
public class ExchangeClient {

    private final RestClient client;

    public ExchangeClient(@Qualifier("exchangeRestClient") RestClient client) {
        this.client = client;
    }

    public ExchangeOrder placeOrder(Long platformUserId, PlaceOrderRequest request) {
        ExchangeOrderRequest body = new ExchangeOrderRequest(
                String.valueOf(platformUserId),
                request.instrumentType().name(),
                request.instrumentId(),
                request.orderType().name(),
                request.side().name(),
                request.quantity(),
                request.limitPrice(),
                request.expiresAt()
        );

        return client.post()
                .uri("/orders")
                .body(body)
                .retrieve()
                .body(ExchangeOrder.class);
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
