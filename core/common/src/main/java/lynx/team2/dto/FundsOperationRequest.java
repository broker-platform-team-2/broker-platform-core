package lynx.team2.dto;

import java.math.BigDecimal;

public record FundsOperationRequest(
        Long userId,
        String currency,
        BigDecimal amount
) {}
