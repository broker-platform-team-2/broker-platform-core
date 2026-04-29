package lynx.team2.client;

import lynx.team2.dto.CreateAccountRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AccountServiceClient {

    private final RestClient client;

    public AccountServiceClient(@Value("${services.account.url:http://localhost:8083}") String baseUrl) {
        this.client = RestClient.builder().baseUrl(baseUrl).build();
    }

    public void createAccount(Long userId, String currency) {
        client.post()
                .uri("/accounts")
                .body(new CreateAccountRequest(userId, currency))
                .retrieve()
                .toBodilessEntity();
    }
}
