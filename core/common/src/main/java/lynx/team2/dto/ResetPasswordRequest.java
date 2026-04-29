package lynx.team2.dto;

public record ResetPasswordRequest(
        String token,
        String newPassword
) {}