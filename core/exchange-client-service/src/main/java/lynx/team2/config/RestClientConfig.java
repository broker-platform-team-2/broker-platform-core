package lynx.team2.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean("accountRestClient")
    public RestClient accountRestClient(@Value("${services.account.url}") String baseUrl) {
        return RestClient.builder().baseUrl(baseUrl).build();
    }

    @Bean("transactionRestClient")
    public RestClient transactionRestClient(@Value("${services.transaction.url}") String baseUrl) {
        return RestClient.builder().baseUrl(baseUrl).build();
    }

    @Bean("holdingsRestClient")
    public RestClient holdingsRestClient(@Value("${services.holdings.url}") String baseUrl) {
        return RestClient.builder().baseUrl(baseUrl).build();
    }

    @Bean("notificationRestClient")
    public RestClient notificationRestClient(@Value("${services.notification.url}") String baseUrl) {
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
}
