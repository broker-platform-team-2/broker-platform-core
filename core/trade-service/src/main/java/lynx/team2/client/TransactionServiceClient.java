package lynx.team2.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import lynx.team2.dto.CreateTransactionRequest;
import lynx.team2.dto.TransactionResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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

    public TransactionResponse createTransaction(CreateTransactionRequest request) {
        return client.post()
                .uri("/transactions")
                .body(request)
                .retrieve()
                .body(TransactionResponse.class);
    }

    /** Returns null if the transaction is not found. */
    public TransactionDto findByExchangeOrderId(String exchangeOrderId) {
        try {
            return client.get()
                    .uri("/transactions/by-exchange-order/{id}", exchangeOrderId)
                    .header("X-Internal-Token", internalToken)
                    .retrieve()
                    .body(TransactionDto.class);
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        }
    }

    public void updateStatus(String exchangeOrderId, String status) {
        client.patch()
                .uri(u -> u.path("/transactions/exchange-order/{id}/status")
                        .queryParam("status", status)
                        .build(exchangeOrderId))
                .header("X-Internal-Token", internalToken)
                .retrieve()
                .toBodilessEntity();
    }

    /** Returns null if the transaction is not found. */
    public TransactionDto findById(Long transactionId) {
        try {
            return client.get()
                    .uri("/transactions/{id}/internal", transactionId)
                    .header("X-Internal-Token", internalToken)
                    .retrieve()
                    .body(TransactionDto.class);
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        }
    }

    public void updateStatusById(Long transactionId, String status) {
        client.patch()
                .uri(u -> u.path("/transactions/{id}/status")
                        .queryParam("status", status)
                        .build(transactionId))
                .header("X-Internal-Token", internalToken)
                .retrieve()
                .toBodilessEntity();
    }

    public record TransactionDto(
            @JsonProperty("transactionId")    Long transactionId,
            @JsonProperty("userId")           Long userId,
            @JsonProperty("exchangeOrderId")  String exchangeOrderId,
            @JsonProperty("type")             String type,
            @JsonProperty("status")           String status,
            @JsonProperty("price")            BigDecimal price,
            @JsonProperty("currency")         String currency,
            @JsonProperty("quantity")         Integer quantity,
            @JsonProperty("date")             LocalDateTime date,
            @JsonProperty("instrumentId")     String instrumentId,
            @JsonProperty("instrumentType")   String instrumentType
    ) {}
}
