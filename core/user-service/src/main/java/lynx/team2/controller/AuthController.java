package lynx.team2.controller;

import lynx.team2.dto.*;
import lynx.team2.models.User;
import lynx.team2.service.EmailService;
import lynx.team2.service.JwtService;
import lynx.team2.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/users")
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;
    private final EmailService emailService;

    public AuthController(
            UserService userService,
            JwtService jwtService,
            EmailService emailService
    ) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.emailService = emailService;
    }

    @PostMapping("/register")
    public AuthResponse register(@RequestBody RegisterRequest request) {
        User user = userService.signUp(
                request.email(),
                request.username(),
                request.password()
        );

        emailService.sendVerificationEmail(
                user.getEmail(),
                user.getEmailVerificationToken()
        );

        String token = jwtService.generateToken(user);

        return new AuthResponse(
                token,
                user.getUserId(),
                user.getUsername(),
                user.getEmail()
        );
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody LoginRequest request) {
        User user = userService.login(request.email(), request.password());

        String token = jwtService.generateToken(user);

        return new AuthResponse(
                token,
                user.getUserId(),
                user.getUsername(),
                user.getEmail()
        );
    }

    @GetMapping("/verify-email")
    public Map<String, String> verifyEmail(@RequestParam String token) {
        userService.verifyEmail(token);

        return Map.of("message", "Email verified successfully");
    }

    @PostMapping("/forgot-password")
    public Map<String, String> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        User user = userService.createPasswordResetToken(request.email());

        emailService.sendPasswordResetEmail(user.getEmail(), user.getPasswordResetToken());

        return Map.of("message", "Password reset email sent");
    }

    @PostMapping("/reset-password")
    public Map<String, String> resetPassword(@RequestBody ResetPasswordRequest request) {
        userService.resetPassword(request.token(), request.newPassword());

        return Map.of("message", "Password reset successfully");
    }

    @PutMapping("/{userId}/password")
    public Map<String, String> changePassword(
            @PathVariable Long userId,
            @RequestBody ChangePasswordRequest request
    ) {
        userService.changePassword(
                userId,
                request.oldPassword(),
                request.newPassword()
        );

        return Map.of("message", "Password changed successfully");
    }

    @PutMapping("/{userId}/username")
    public AuthResponse changeUsername(
            @PathVariable Long userId,
            @RequestBody ChangeUsernameRequest request
    ) {
        User user = userService.changeUsername(userId, request.newUsername());

        String token = jwtService.generateToken(user);

        return new AuthResponse(
                token,
                user.getUserId(),
                user.getUsername(),
                user.getEmail()
        );
    }
}
