package lynx.team2.dto;

import java.math.BigDecimal;

public record FundsOperationRequest(
        Long userId,
        BigDecimal amount
) {}
