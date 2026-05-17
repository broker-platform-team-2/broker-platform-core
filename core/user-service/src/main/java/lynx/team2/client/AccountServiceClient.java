package lynx.team2.client;

import lynx.team2.dto.CreateAccountRequest;
import lynx.team2.dto.DepositRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

@Component
public class AccountServiceClient {

    private final RestClient client;

    public AccountServiceClient(@Value("${services.account.url:http://localhost:8183}") String baseUrl) {
        this.client = RestClient.builder().baseUrl(baseUrl).build();
    }

    public void createAccount(Long userId, String currency) {
        client.post()
                .uri("/accounts")
                .header("X-User-Id", String.valueOf(userId))
                .body(new CreateAccountRequest(userId, currency))
                .retrieve()
                .toBodilessEntity();
    }

    public void deductFunds(Long userId, String currency, BigDecimal amount) {
        client.post()
                .uri("/funds/withdraw")
                .header("X-User-Id", String.valueOf(userId))
                .body(new DepositRequest(currency, amount))
                .retrieve()
                .toBodilessEntity();
    }
}
