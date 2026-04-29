package lynx.team2.dto;

import java.util.UUID;

public record AuthResponse(
        String token,
        Long userId,
        String username,
        String email
) {}
