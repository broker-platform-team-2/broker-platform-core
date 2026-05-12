package lynx.team2.client;

import lynx.team2.dto.NotificationMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class NotificationServiceClient {

    private final RestClient client;
    private final String internalToken;

    public NotificationServiceClient(
            @Qualifier("notificationRestClient") RestClient client,
            @Value("${internal.token}") String internalToken) {
        this.client = client;
        this.internalToken = internalToken;
    }

    public void notifyUser(Long userId, NotificationMessage message) {
        client.post()
                .uri("/notify/{userId}", userId)
                .header("X-Internal-Token", internalToken)
                .body(message)
                .retrieve()
                .toBodilessEntity();
    }

    public void broadcast(NotificationMessage message) {
        client.post()
                .uri("/broadcast")
                .header("X-Internal-Token", internalToken)
                .body(message)
                .retrieve()
                .toBodilessEntity();
    }
}
