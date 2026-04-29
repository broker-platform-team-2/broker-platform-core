package lynx.team2.dto;

import lynx.team2.models.InstrumentType;
import lynx.team2.models.OrderType;
import lynx.team2.models.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PlaceOrderRequest(
        InstrumentType instrumentType,
        String instrumentId,
        OrderType orderType,
        TransactionType side,
        Integer quantity,
        BigDecimal limitPrice,
        LocalDateTime expiresAt
) {}
