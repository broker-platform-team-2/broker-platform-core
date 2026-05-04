package lynx.team2.controller;

import lynx.team2.dto.AccountResponse;
import lynx.team2.dto.CreateAccountRequest;
import lynx.team2.dto.DepositRequest;
import lynx.team2.dto.FundsOperationRequest;
import lynx.team2.models.Account;
import lynx.team2.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    // --- User-facing endpoints (userId injected by gateway as X-User-Id header) ---

    @GetMapping("/accounts/me")
    public AccountResponse getMyAccount(@RequestHeader("X-User-Id") Long userId) {
        return toResponse(accountService.getAccountByUserId(userId));
    }

    @PostMapping("/funds/deposit")
    public AccountResponse deposit(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody DepositRequest request
    ) {
        return toResponse(accountService.depositFunds(userId, request.amount()));
    }

    @GetMapping("/funds/balance")
    public Map<String, Object> getBalance(@RequestHeader("X-User-Id") Long userId) {
        return Map.of(
                "available", accountService.getAvailableBalance(userId),
                "total", accountService.getTotalEquity(userId),
                "currency", accountService.getAccountCurrency(userId)
        );
    }

    // --- Internal endpoints (called by other services; userId in request body) ---

    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse createAccount(@RequestBody CreateAccountRequest request) {
        return toResponse(accountService.createAccount(request.userId(), request.currency()));
    }

    @PostMapping("/funds/freeze")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void freezeFunds(@RequestBody FundsOperationRequest request) {
        accountService.freezeFunds(request.userId(), request.amount());
    }

    @PostMapping("/funds/unfreeze")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfreezeFunds(@RequestBody FundsOperationRequest request) {
        accountService.unfreezeFunds(request.userId(), request.amount());
    }

    @PostMapping("/funds/deduct")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deductFrozenFunds(@RequestBody FundsOperationRequest request) {
        accountService.deductFrozenFunds(request.userId(), request.amount());
    }

    private AccountResponse toResponse(Account account) {
        return new AccountResponse(
                account.getAccountId(),
                account.getUser().getUserId(),
                account.getBalance(),
                account.getFrozenBalance(),
                account.getCurrency()
        );
    }
}
