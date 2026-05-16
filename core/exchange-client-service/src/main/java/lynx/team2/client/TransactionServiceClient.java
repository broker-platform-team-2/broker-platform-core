package lynx.team2.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class TransactionServiceClient {

    private final RestClient client;
    private final String internalToken;

    public TransactionServiceClient(
            @Qualifier("transactionRestClient") RestClient client,
            @Value("${internal.token}") String internalToken) {
        this.client = client;
        this.internalToken = internalToken;
    }

    public TransactionDto findByExchangeOrderId(String exchangeOrderId) {
        return client.get()
                .uri("/transactions/by-exchange-order/{id}", exchangeOrderId)
                .header("X-Internal-Token", internalToken)
                .retrieve()
                .body(TransactionDto.class);
    }

    public List<TransactionDto> findAllPending() {
        return client.get()
                .uri("/transactions/by-status?status=PENDING")
                .header("X-Internal-Token", internalToken)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    public void updateStatus(String exchangeOrderId, String status) {
        client.patch()
                .uri("/transactions/exchange-order/{id}/status?status={status}", exchangeOrderId, status)
                .header("X-Internal-Token", internalToken)
                .retrieve()
                .toBodilessEntity();
    }

    public record TransactionDto(
            @JsonProperty("transactionId") Long transactionId,
            @JsonProperty("userId") Long userId,
            @JsonProperty("exchangeOrderId") String exchangeOrderId,
            @JsonProperty("type") String type,
            @JsonProperty("status") String status,
            @JsonProperty("price") BigDecimal price,
            @JsonProperty("currency") String currency,
            @JsonProperty("quantity") Integer quantity,
            @JsonProperty("date") LocalDateTime date,
            @JsonProperty("instrumentId") String instrumentId,
            @JsonProperty("instrumentType") String instrumentType
    ) {}
}
