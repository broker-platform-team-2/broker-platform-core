package lynx.team2.controller;

import lynx.team2.client.AccountServiceClient;
import lynx.team2.models.User;
import lynx.team2.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/bots")
@RequiredArgsConstructor
public class BotController {

    static final BigDecimal MONTHLY_FEE_USD = new BigDecimal("49.99");

    private final UserRepository userRepository;
    private final AccountServiceClient accountServiceClient;

    @GetMapping("/any-active")
    public Map<String, Object> anyActive() {
        boolean enabled = userRepository.existsByBotSubscribedUntilAfterAndBotRunningTrue(LocalDateTime.now());
        return Map.of("enabled", enabled);
    }

    @GetMapping("/active-subscribers")
    public java.util.List<Map<String, Object>> activeSubscribers() {
        return userRepository.findByBotSubscribedUntilAfterAndBotRunningTrue(LocalDateTime.now())
                .stream()
                .map(u -> Map.<String, Object>of("userId", u.getUserId()))
                .toList();
    }

    @GetMapping("/status")
    public Map<String, Object> getStatus(@RequestHeader("X-User-Id") Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        boolean active = user.getBotSubscribedUntil() != null
                && user.getBotSubscribedUntil().isAfter(LocalDateTime.now());
        return Map.of(
                "active", active,
                "running", active && user.isBotRunning(),
                "subscribedUntil", user.getBotSubscribedUntil() != null ? user.getBotSubscribedUntil().toString() : ""
        );
    }

    @PostMapping("/subscribe")
    public Map<String, Object> subscribe(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "USD") String currency
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        accountServiceClient.deductFunds(userId, currency, MONTHLY_FEE_USD);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = (user.getBotSubscribedUntil() != null && user.getBotSubscribedUntil().isAfter(now))
                ? user.getBotSubscribedUntil()
                : now;
        user.setBotSubscribedUntil(from.plusDays(30));
        user.setBotRunning(true);
        userRepository.save(user);

        return Map.of(
                "active", true,
                "running", true,
                "subscribedUntil", user.getBotSubscribedUntil().toString()
        );
    }

    @PostMapping("/start")
    public Map<String, Object> start(@RequestHeader("X-User-Id") Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        boolean active = user.getBotSubscribedUntil() != null
                && user.getBotSubscribedUntil().isAfter(LocalDateTime.now());
        if (!active) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "Active subscription required");
        }
        user.setBotRunning(true);
        userRepository.save(user);
        return Map.of("active", true, "running", true,
                "subscribedUntil", user.getBotSubscribedUntil().toString());
    }

    @PostMapping("/stop")
    public Map<String, Object> stop(@RequestHeader("X-User-Id") Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        user.setBotRunning(false);
        userRepository.save(user);
        boolean active = user.getBotSubscribedUntil() != null
                && user.getBotSubscribedUntil().isAfter(LocalDateTime.now());
        return Map.of("active", active, "running", false,
                "subscribedUntil", user.getBotSubscribedUntil() != null ? user.getBotSubscribedUntil().toString() : "");
    }
}