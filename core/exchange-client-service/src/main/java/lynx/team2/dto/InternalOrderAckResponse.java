package lynx.team2.dto;

import java.time.LocalDateTime;

/**
 * Response returned to trade-service after the exchange acknowledges the order.
 */
public record InternalOrderAckResponse(
        String orderId,
        String status,
        LocalDateTime createdAt
) {}
