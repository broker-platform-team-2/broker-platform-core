package lynx.team2.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lynx.team2.client.AccountServiceClient;
import lynx.team2.client.ExchangeClient;
import lynx.team2.client.TransactionServiceClient;
import lynx.team2.dto.CreateTransactionRequest;
import lynx.team2.dto.OrderResponse;
import lynx.team2.dto.PlaceOrderRequest;
import lynx.team2.dto.TransactionResponse;
import lynx.team2.exceptions.ValidatorException;
import lynx.team2.models.OrderType;
import lynx.team2.models.TransactionStatus;
import lynx.team2.models.TransactionType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradeService {

    private final AccountServiceClient accountServiceClient;
    private final TransactionServiceClient transactionServiceClient;
    private final ExchangeClient exchangeClient;

    public OrderResponse placeOrder(Long userId, PlaceOrderRequest request) {
        validate(request);

        BigDecimal estimatedCost = estimateCost(request);
        boolean shouldFreeze = request.side() == TransactionType.BUY && estimatedCost != null;

        if (shouldFreeze) {
            accountServiceClient.freezeFunds(userId, estimatedCost);
        }

        ExchangeClient.ExchangeOrder exchangeOrder;
        try {
            exchangeOrder = exchangeClient.placeOrder(userId, request);
        } catch (RuntimeException e) {
            if (shouldFreeze) {
                safeUnfreeze(userId, estimatedCost);
            }
            throw e;
        }

        TransactionResponse tx = transactionServiceClient.createTransaction(new CreateTransactionRequest(
                userId,
                parseExchangeOrderId(exchangeOrder.orderId()),
                request.side(),
                TransactionStatus.PENDING,
                BigDecimal.ZERO,
                resolvePrice(request, exchangeOrder),
                "USD",
                request.quantity()
        ));

        return new OrderResponse(
                parseExchangeOrderId(exchangeOrder.orderId()),
                tx.transactionId(),
                request.instrumentType(),
                request.instrumentId(),
                request.orderType(),
                request.side(),
                request.quantity(),
                request.limitPrice(),
                TransactionStatus.PENDING,
                exchangeOrder.createdAt()
        );
    }

    public ExchangeClient.ExchangeOrder getOrder(String orderId) {
        return exchangeClient.getOrder(orderId);
    }

    public void cancelOrder(String orderId) {
        exchangeClient.cancelOrder(orderId);
    }

    private void validate(PlaceOrderRequest request) {
        if (request.instrumentType() == null) {
            throw new ValidatorException("instrumentType is required");
        }
        if (request.instrumentId() == null || request.instrumentId().isBlank()) {
            throw new ValidatorException("instrumentId is required");
        }
        if (request.orderType() == null) {
            throw new ValidatorException("orderType is required");
        }
        if (request.side() == null) {
            throw new ValidatorException("side is required");
        }
        if (request.quantity() == null || request.quantity() <= 0) {
            throw new ValidatorException("quantity must be greater than zero");
        }
        if (request.orderType() == OrderType.LIMIT
                && (request.limitPrice() == null || request.limitPrice().compareTo(BigDecimal.ZERO) <= 0)) {
            throw new ValidatorException("limitPrice is required for LIMIT orders and must be positive");
        }
    }

    private BigDecimal estimateCost(PlaceOrderRequest request) {
        BigDecimal qty = BigDecimal.valueOf(request.quantity());
        return switch (request.orderType()) {
            case LIMIT -> request.limitPrice().multiply(qty);
            case MARKET -> {
                try {
                    ExchangeClient.StockSnapshot stock = exchangeClient.getStock(request.instrumentId());
                    BigDecimal buffer = BigDecimal.valueOf(1.05);
                    yield stock.currentPrice().multiply(qty).multiply(buffer);
                } catch (RuntimeException e) {
                    log.warn("Could not fetch market price for {}; skipping freeze", request.instrumentId(), e);
                    yield null;
                }
            }
        };
    }

    private BigDecimal resolvePrice(PlaceOrderRequest request, ExchangeClient.ExchangeOrder exchangeOrder) {
        if (request.limitPrice() != null) {
            return request.limitPrice();
        }
        if (exchangeOrder.averageFillPrice() != null) {
            return exchangeOrder.averageFillPrice();
        }
        return BigDecimal.ZERO;
    }

    private Long parseExchangeOrderId(String id) {
        if (id == null) return null;
        try {
            return Long.parseLong(id.replaceAll("\\D", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void safeUnfreeze(Long userId, BigDecimal amount) {
        try {
            accountServiceClient.unfreezeFunds(userId, amount);
        } catch (RuntimeException e) {
            log.error("Failed to unfreeze funds for user {} amount {}; manual reconciliation required",
                    userId, amount, e);
        }
    }
}
