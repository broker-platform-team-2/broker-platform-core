package lynx.team2.dto;

public record RegisterRequest (
    String email,
    String username,
    String password
) {}
