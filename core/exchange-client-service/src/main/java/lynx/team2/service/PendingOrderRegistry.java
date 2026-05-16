package lynx.team2.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks in-flight PLACE_ORDER messages sent over WebSocket.
 * When the exchange replies with ORDER_ACK or ORDER_REJECTED the
 * corresponding future is resolved so the calling thread can unblock.
 */
@Component
public class PendingOrderRegistry {

    public record AckResult(String orderId, String status) {}

    public static class OrderRejectedException extends RuntimeException {
        private final String code;
        public OrderRejectedException(String orderId, String code, String message) {
            super("Order " + orderId + " rejected [" + code + "]: " + message);
            this.code = code;
        }
        public String getCode() { return code; }
    }

    private final ConcurrentHashMap<String, CompletableFuture<AckResult>> pending = new ConcurrentHashMap<>();

    public CompletableFuture<AckResult> register(String orderId) {
        CompletableFuture<AckResult> future = new CompletableFuture<>();
        pending.put(orderId, future);
        return future;
    }

    public void ack(String orderId) {
        CompletableFuture<AckResult> future = pending.remove(orderId);
        if (future != null) {
            future.complete(new AckResult(orderId, "PENDING"));
        }
    }

    public void reject(String orderId, String code, String message) {
        CompletableFuture<AckResult> future = pending.remove(orderId);
        if (future != null) {
            future.completeExceptionally(new OrderRejectedException(orderId, code, message));
        }
    }

    /** Called on disconnect to fail all waiting futures immediately. */
    public void failAll(String reason) {
        pending.forEach((id, future) ->
                future.completeExceptionally(new RuntimeException("WebSocket disconnected: " + reason)));
        pending.clear();
    }
}
