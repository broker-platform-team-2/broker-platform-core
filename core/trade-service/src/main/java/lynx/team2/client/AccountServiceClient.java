package lynx.team2.client;

import lynx.team2.dto.FundsOperationRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

@Component
public class AccountServiceClient {

    private final RestClient client;

    public AccountServiceClient(@Qualifier("accountServiceClient") RestClient client) {
        this.client = client;
    }

    public void freezeFunds(Long userId, BigDecimal amount) {
        client.post()
                .uri("/funds/freeze")
                .body(new FundsOperationRequest(userId, amount))
                .retrieve()
                .toBodilessEntity();
    }

    public void unfreezeFunds(Long userId, BigDecimal amount) {
        client.post()
                .uri("/funds/unfreeze")
                .body(new FundsOperationRequest(userId, amount))
                .retrieve()
                .toBodilessEntity();
    }

    public void deductFrozenFunds(Long userId, BigDecimal amount) {
        client.post()
                .uri("/funds/deduct")
                .body(new FundsOperationRequest(userId, amount))
                .retrieve()
                .toBodilessEntity();
    }
}
