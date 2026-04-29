package lynx.team2.client;

import lynx.team2.dto.CreateTransactionRequest;
import lynx.team2.dto.TransactionResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class TransactionServiceClient {

    private final RestClient client;

    public TransactionServiceClient(@Qualifier("transactionServiceClient") RestClient client) {
        this.client = client;
    }

    public TransactionResponse createTransaction(CreateTransactionRequest request) {
        return client.post()
                .uri("/transactions")
                .body(request)
                .retrieve()
                .body(TransactionResponse.class);
    }
}
