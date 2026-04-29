package lynx.team2.dto;

import java.util.Map;

public record NotificationMessage(
        String type,
        Map<String, Object> payload
) {}
