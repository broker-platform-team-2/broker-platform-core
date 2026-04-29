package lynx.team2.dto;

import java.math.BigDecimal;

public record UpdateHoldingRequest(
        BigDecimal amount,
        BigDecimal averageCost
) {}
