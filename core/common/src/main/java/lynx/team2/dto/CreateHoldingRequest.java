package lynx.team2.dto;

import lynx.team2.models.InstrumentType;

import java.math.BigDecimal;

public record CreateHoldingRequest(
        Long userId,
        InstrumentType instrumentType,
        String instrumentId,
        BigDecimal amount,
        String currency,
        BigDecimal averageCost
) {}
