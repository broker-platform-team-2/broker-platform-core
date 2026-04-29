package lynx.team2.dto;

public record ChangePasswordRequest(
        String oldPassword,
        String newPassword
) {}
