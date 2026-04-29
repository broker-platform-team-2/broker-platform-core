package lynx.team2.dto;

public record CreateAccountRequest(
        Long userId,
        String currency
) {}
