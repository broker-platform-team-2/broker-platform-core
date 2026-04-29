package lynx.team2.client;

import lynx.team2.dto.NotificationMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class NotificationServiceClient {

    private final RestClient client;

    public NotificationServiceClient(@Qualifier("notificationServiceClient") RestClient client) {
        this.client = client;
    }

    public void notifyUser(Long userId, NotificationMessage message) {
        client.post()
                .uri("/notify/{userId}", userId)
                .body(message)
                .retrieve()
                .toBodilessEntity();
    }

    public void broadcast(NotificationMessage message) {
        client.post()
                .uri("/broadcast")
                .body(message)
                .retrieve()
                .toBodilessEntity();
    }
}
