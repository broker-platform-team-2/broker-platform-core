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
import lynx.team2.util.CurrencyConverter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

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

        String currency = (request.currency() != null && !request.currency().isBlank())
                ? request.currency() : "USD";

        BigDecimal estimatedCostUSD = estimateCost(request);
        BigDecimal estimatedCost = estimatedCostUSD != null
                ? CurrencyConverter.fromUSD(estimatedCostUSD, currency) : null;
        boolean shouldFreeze = request.side() == TransactionType.BUY && estimatedCost != null;

        if (shouldFreeze) {
            accountServiceClient.freezeFunds(userId, currency, estimatedCost);
        }

        ExchangeClient.ExchangeOrder exchangeOrder;
        try {
            exchangeOrder = exchangeClient.placeOrder(userId, request);
        } catch (RuntimeException e) {
            if (shouldFreeze) {
                safeUnfreeze(userId, currency, estimatedCost);
            }
            throw e;
        }

        BigDecimal resolvedPrice = resolvePrice(request, exchangeOrder);
        BigDecimal platformFee = calculateFee(resolvedPrice, request.quantity(), currency);

        TransactionResponse tx = transactionServiceClient.createTransaction(new CreateTransactionRequest(
                userId,
                exchangeOrder.orderId(),
                request.side(),
                TransactionStatus.PENDING,
                platformFee,
                resolvedPrice,
                currency,
                request.quantity(),
                request.instrumentId(),
                request.instrumentType() != null ? request.instrumentType().name() : null
        ));

        return new OrderResponse(
                exchangeOrder.orderId(),
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

    public ExchangeClient.ExchangeOrder getOrder(Long userId, String orderId) {
        ExchangeClient.ExchangeOrder order = exchangeClient.getOrder(orderId);
        if (!String.valueOf(userId).equals(order.platformUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
        return order;
    }

    public void cancelOrder(Long userId, String orderId) {
        ExchangeClient.ExchangeOrder order = exchangeClient.getOrder(orderId);
        if (!String.valueOf(userId).equals(order.platformUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
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
        boolean isOption = request.instrumentType() == lynx.team2.models.InstrumentType.OPTION;
        return switch (request.orderType()) {
            case LIMIT -> request.limitPrice().multiply(qty);
            case MARKET -> {
                try {
                    BigDecimal unitPrice;
                    if (isOption) {
                        unitPrice = exchangeClient.getOptionPremium(request.instrumentId());
                    } else {
                        unitPrice = exchangeClient.getStock(request.instrumentId()).currentPrice();
                    }
                    if (unitPrice == null) yield null;
                    yield unitPrice.multiply(qty).multiply(BigDecimal.valueOf(1.05));
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
        if (exchangeOrder.averageFillPrice() != null
                && exchangeOrder.averageFillPrice().compareTo(BigDecimal.ZERO) > 0) {
            return exchangeOrder.averageFillPrice();
        }
        // Market order not yet filled — use current market price as placeholder
        try {
            boolean isOption = request.instrumentType() == lynx.team2.models.InstrumentType.OPTION;
            BigDecimal unitPrice = isOption
                    ? exchangeClient.getOptionPremium(request.instrumentId())
                    : exchangeClient.getStock(request.instrumentId()).currentPrice();
            if (unitPrice != null && unitPrice.compareTo(BigDecimal.ZERO) > 0) {
                return unitPrice;
            }
        } catch (RuntimeException e) {
            log.warn("Could not fetch market price for {} to resolve transaction price", request.instrumentId(), e);
        }
        return BigDecimal.ONE;
    }

    /**
     * Platform fee: 0.08% of order value, minimum $1.00 equivalent.
     * Stored in the account currency so the Orders page can display it directly.
     */
    private BigDecimal calculateFee(BigDecimal priceUSD, Integer quantity, String currency) {
        if (priceUSD == null || quantity == null || quantity <= 0) {
            return BigDecimal.valueOf(1.00);
        }
        BigDecimal orderValueUSD = priceUSD.multiply(BigDecimal.valueOf(quantity));
        BigDecimal feeUSD = orderValueUSD.multiply(BigDecimal.valueOf(0.0008));
        BigDecimal minFeeUSD = BigDecimal.valueOf(1.00);
        BigDecimal feeInUSD = feeUSD.compareTo(minFeeUSD) < 0 ? minFeeUSD : feeUSD;
        return CurrencyConverter.fromUSD(feeInUSD, currency)
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private void safeUnfreeze(Long userId, String currency, BigDecimal amount) {
        try {
            accountServiceClient.unfreezeFunds(userId, currency, amount);
        } catch (RuntimeException e) {
            log.error("Failed to unfreeze funds for user {} amount {} {}; manual reconciliation required",
                    userId, amount, currency, e);
        }
    }
}
