package lynx.team2.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean("accountServiceClient")
    public RestClient accountServiceClient(@Value("${services.account.url}") String baseUrl) {
        return RestClient.builder().baseUrl(baseUrl).build();
    }

    @Bean("transactionServiceClient")
    public RestClient transactionServiceClient(@Value("${services.transaction.url}") String baseUrl) {
        return RestClient.builder().baseUrl(baseUrl).build();
    }

    @Bean("holdingsServiceClient")
    public RestClient holdingsServiceClient(@Value("${services.holdings.url}") String baseUrl) {
        return RestClient.builder().baseUrl(baseUrl).build();
    }

    @Bean("notificationServiceClient")
    public RestClient notificationServiceClient(@Value("${services.notification.url}") String baseUrl) {
        return RestClient.builder().baseUrl(baseUrl).build();
    }
}
