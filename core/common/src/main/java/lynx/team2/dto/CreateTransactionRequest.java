package lynx.team2.dto;

import lynx.team2.models.TransactionStatus;
import lynx.team2.models.TransactionType;

import java.math.BigDecimal;

public record CreateTransactionRequest(
        Long userId,
        Long exchangeOrderId,
        TransactionType type,
        TransactionStatus status,
        BigDecimal platformFee,
        BigDecimal price,
        String currency,
        Integer quantity
) {}
