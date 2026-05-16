package lynx.team2.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lynx.team2.dto.InternalOrderAckResponse;
import lynx.team2.dto.InternalPlaceOrderRequest;
import lynx.team2.service.OrderPlacementService;
import lynx.team2.service.PendingOrderRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

/**
 * Internal endpoint called by trade-service to place orders via the exchange WebSocket.
 * Not exposed through the public gateway.
 */
@Slf4j
@RestController
@RequestMapping("/internal/orders")
@RequiredArgsConstructor
public class InternalOrderController {

    private final OrderPlacementService orderPlacementService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InternalOrderAckResponse placeOrder(@RequestBody InternalPlaceOrderRequest request) {
        try {
            PendingOrderRegistry.AckResult ack = orderPlacementService.placeOrder(request);
            return new InternalOrderAckResponse(ack.orderId(), ack.status(), LocalDateTime.now());
        } catch (PendingOrderRegistry.OrderRejectedException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (RuntimeException e) {
            log.error("Failed to place order {} via WebSocket", request.orderId(), e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        }
    }
}
