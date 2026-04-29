package lynx.team2.dto;

import java.math.BigDecimal;

public record AccountResponse(
        Long accountId,
        Long userId,
        BigDecimal balance,
        BigDecimal frozenBalance,
        String currency
) {}
