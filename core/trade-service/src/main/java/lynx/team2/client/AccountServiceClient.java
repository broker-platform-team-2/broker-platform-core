package lynx.team2.client;

import lynx.team2.dto.FundsOperationRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

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

    public void freezeFunds(Long userId, String currency, BigDecimal amount) {
        client.post()
                .uri("/funds/freeze")
                .header("X-Internal-Token", internalToken)
                .body(new FundsOperationRequest(userId, currency, amount))
                .retrieve()
                .toBodilessEntity();
    }

    public void unfreezeFunds(Long userId, String currency, BigDecimal amount) {
        client.post()
                .uri("/funds/unfreeze")
                .header("X-Internal-Token", internalToken)
                .body(new FundsOperationRequest(userId, currency, amount))
                .retrieve()
                .toBodilessEntity();
    }

    public void deductFrozenFunds(Long userId, String currency, BigDecimal amount) {
        client.post()
                .uri("/funds/deduct")
                .header("X-Internal-Token", internalToken)
                .body(new FundsOperationRequest(userId, currency, amount))
                .retrieve()
                .toBodilessEntity();
    }
}
