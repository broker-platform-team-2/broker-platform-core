package lynx.team2.dto;

import lynx.team2.models.FundsOperationType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FundsOperationResponse(
        Long operationId,
        FundsOperationType operationType,
        BigDecimal amount,
        String currency,
        LocalDateTime date
) {}
