package lynx.team2.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean("accountRestClient")
    public RestClient accountServiceClient(@Value("${services.account.url}") String baseUrl) {
        return RestClient.builder().baseUrl(baseUrl).build();
    }

    @Bean("transactionRestClient")
    public RestClient transactionServiceClient(@Value("${services.transaction.url}") String baseUrl) {
        return RestClient.builder().baseUrl(baseUrl).build();
    }

    @Bean("holdingsServiceClient")
    public RestClient holdingsServiceClient(@Value("${services.holdings.url}") String baseUrl) {
        return RestClient.builder().baseUrl(baseUrl).build();
    }

    @Bean("exchangeRestClient")
    public RestClient exchangeRestClient(
            @Value("${exchange.base-url}") String baseUrl,
            @Value("${exchange.api-key}") String apiKey,
            @Value("${exchange.api-secret}") String apiSecret
    ) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("API-KEY", apiKey)
                .defaultHeader("API-SECRET", apiSecret)
                .build();
    }

    /** Calls exchange-client-service to place orders via its live WebSocket connection */
    @Bean("exchangeClientRestClient")
    public RestClient exchangeClientRestClient(
            @Value("${services.exchange-client.url}") String baseUrl,
            @Value("${internal.token}") String internalToken
    ) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Internal-Token", internalToken)
                .build();
    }
}
