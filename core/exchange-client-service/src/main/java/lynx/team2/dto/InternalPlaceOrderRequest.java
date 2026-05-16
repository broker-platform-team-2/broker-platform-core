package lynx.team2.dto;

import java.math.BigDecimal;

/**
 * Payload sent from trade-service to exchange-client-service
 * to place an order via the exchange WebSocket.
 */
public record InternalPlaceOrderRequest(
        String orderId,
        String platformUserId,
        String instrumentType,
        String instrumentId,
        String orderType,
        String side,
        Integer quantity,
        BigDecimal limitPrice,
        String expiresAt
) {}
