package lynx.team2.controller;

import lombok.RequiredArgsConstructor;
import lynx.team2.client.ExchangeClient;
import lynx.team2.dto.OrderResponse;
import lynx.team2.dto.PlaceOrderRequest;
import lynx.team2.service.TradeService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class TradeController {

    private final TradeService tradeService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse placeOrder(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody PlaceOrderRequest request
    ) {
        return tradeService.placeOrder(userId, request);
    }

    @GetMapping("/{orderId}")
    public ExchangeClient.ExchangeOrder getOrder(@PathVariable String orderId) {
        return tradeService.getOrder(orderId);
    }

    @DeleteMapping("/{orderId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelOrder(@PathVariable String orderId) {
        tradeService.cancelOrder(orderId);
    }
}
