package lynx.team2.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class AccountServiceClient {

    private final RestClient client;
    private final String internalToken;

    public AccountServiceClient(
            @Qualifier("accountRestClient") RestClient client,
            @Value("${internal.token}") String internalToken) {
        this.client = client;
        this.internalToken = internalToken;
    }

    public void deductFrozenFunds(Long userId, String currency, BigDecimal amount) {
        client.post()
                .uri("/funds/deduct/frozen")
                .header("X-Internal-Token", internalToken)
                .body(Map.of("userId", userId, "currency", currency, "amount", amount))
                .retrieve()
                .toBodilessEntity();
    }

    public void unfreezeFunds(Long userId, String currency, BigDecimal amount) {
        client.post()
                .uri("/funds/unfreeze")
                .header("X-Internal-Token", internalToken)
                .body(Map.of("userId", userId, "currency", currency, "amount", amount))
                .retrieve()
                .toBodilessEntity();
    }

    public void depositFunds(Long userId, String currency, BigDecimal amount) {
        client.post()
                .uri("/funds/deposit")
                .header("X-User-Id", userId.toString())
                .body(Map.of("currency", currency, "amount", amount))
                .retrieve()
                .toBodilessEntity();
    }
}
