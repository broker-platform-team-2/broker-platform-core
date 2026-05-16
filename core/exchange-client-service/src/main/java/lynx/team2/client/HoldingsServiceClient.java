package lynx.team2.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

@Component
public class HoldingsServiceClient {

    private final RestClient client;
    private final String internalToken;

    public HoldingsServiceClient(
            @Qualifier("holdingsRestClient") RestClient client,
            @Value("${internal.token}") String internalToken) {
        this.client = client;
        this.internalToken = internalToken;
    }

    public void upsertHolding(Long userId, String instrumentType, String instrumentId,
                               BigDecimal quantity, String currency, BigDecimal avgPrice) {
        List<HoldingDto> existing = client.get()
                .uri("/holdings?instrumentId={id}", instrumentId)
                .header("X-User-Id", userId.toString())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        HoldingDto match = existing == null ? null :
                existing.stream().filter(h -> instrumentId.equals(h.instrumentId())).findFirst().orElse(null);

        if (match == null) {
            client.post()
                    .uri("/holdings")
                    .body(Map.of(
                            "userId", userId,
                            "instrumentType", instrumentType,
                            "instrumentId", instrumentId,
                            "amount", quantity,
                            "currency", currency,
                            "averageCost", avgPrice
                    ))
                    .retrieve()
                    .toBodilessEntity();
        } else {
            BigDecimal oldAmt = match.amount();
            BigDecimal newAmt = oldAmt.add(quantity);
            BigDecimal newAvg = newAmt.compareTo(BigDecimal.ZERO) > 0
                    ? oldAmt.multiply(match.averageCost()).add(quantity.multiply(avgPrice))
                            .divide(newAmt, 8, RoundingMode.HALF_UP)
                    : avgPrice;
            client.put()
                    .uri("/holdings/{id}", match.holdingId())
                    .header("X-User-Id", userId.toString())
                    .body(Map.of("amount", newAmt, "averageCost", newAvg))
                    .retrieve()
                    .toBodilessEntity();
        }
    }

    public void reduceHolding(Long userId, String instrumentId, BigDecimal quantity) {
        List<HoldingDto> existing = client.get()
                .uri("/holdings?instrumentId={id}", instrumentId)
                .header("X-User-Id", userId.toString())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        if (existing == null) return;
        HoldingDto match = existing.stream().filter(h -> instrumentId.equals(h.instrumentId())).findFirst().orElse(null);
        if (match == null) return;

        BigDecimal newAmt = match.amount().subtract(quantity);
        if (newAmt.compareTo(BigDecimal.ZERO) <= 0) {
            client.delete()
                    .uri("/holdings/{id}", match.holdingId())
                    .header("X-User-Id", userId.toString())
                    .retrieve()
                    .toBodilessEntity();
        } else {
            client.put()
                    .uri("/holdings/{id}", match.holdingId())
                    .header("X-User-Id", userId.toString())
                    .body(Map.of("amount", newAmt, "averageCost", match.averageCost()))
                    .retrieve()
                    .toBodilessEntity();
        }
    }

    public record HoldingDto(
            @JsonProperty("holdingId") Long holdingId,
            @JsonProperty("userId") Long userId,
            @JsonProperty("instrumentType") String instrumentType,
            @JsonProperty("instrumentId") String instrumentId,
            @JsonProperty("amount") BigDecimal amount,
            @JsonProperty("currency") String currency,
            @JsonProperty("averageCost") BigDecimal averageCost
    ) {}
}
