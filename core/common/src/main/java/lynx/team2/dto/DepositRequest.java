package lynx.team2.dto;

import java.math.BigDecimal;

public record DepositRequest(
        String currency,
        BigDecimal amount
) {}
