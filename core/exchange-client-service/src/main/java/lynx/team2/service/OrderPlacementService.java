package lynx.team2.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lynx.team2.client.ExchangeWebSocketClient;
import lynx.team2.dto.InternalPlaceOrderRequest;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Sends a PLACE_ORDER message to the exchange over the existing WebSocket connection
 * and blocks until the exchange replies with ORDER_ACK or ORDER_REJECTED (10 s timeout).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderPlacementService {

    private static final int ACK_TIMEOUT_SECONDS = 10;

    private final ExchangeWebSocketClient wsClient;
    private final PendingOrderRegistry registry;
    private final ObjectMapper objectMapper;

    public PendingOrderRegistry.AckResult placeOrder(InternalPlaceOrderRequest req) {
        String orderId = req.orderId();

        // Register future BEFORE sending so we can't miss the ACK
        CompletableFuture<PendingOrderRegistry.AckResult> future = registry.register(orderId);

        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("order_id",        orderId);
            payload.put("platform_user_id", req.platformUserId());
            payload.put("instrument_type",  req.instrumentType());
            payload.put("instrument_id",    req.instrumentId());
            payload.put("order_type",       req.orderType());
            payload.put("side",             req.side());
            payload.put("quantity",         req.quantity());
            if (req.limitPrice() != null) payload.put("limit_price", req.limitPrice());
            if (req.expiresAt()  != null) payload.put("expires_at",  req.expiresAt());

            Map<String, Object> message = Map.of("type", "PLACE_ORDER", "payload", payload);
            wsClient.send(objectMapper.writeValueAsString(message));
            log.info("Sent PLACE_ORDER via WebSocket: order_id={} side={} instrument={}",
                    orderId, req.side(), req.instrumentId());

        } catch (Exception e) {
            registry.reject(orderId, "SEND_FAILED", e.getMessage());
        }

        try {
            return future.get(ACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            registry.reject(orderId, "TIMEOUT", "No ACK received within " + ACK_TIMEOUT_SECONDS + "s");
            throw new RuntimeException("Exchange did not acknowledge order " + orderId + " within timeout");
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new RuntimeException(cause.getMessage(), cause);
        }
    }
}
