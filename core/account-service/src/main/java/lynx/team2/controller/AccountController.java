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

import java.util.List;

@RestController
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    // --- User-facing endpoints (userId injected by gateway as X-User-Id header) ---

    /** All accounts the caller owns, primary first. The frontend computes "available" per currency. */
    @GetMapping("/accounts/me")
    public List<AccountResponse> getMyAccounts(@RequestHeader("X-User-Id") Long userId) {
        return accountService.getAccountsByUserId(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping("/funds/deposit")
    public AccountResponse deposit(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody DepositRequest request
    ) {
        return toResponse(accountService.depositFunds(userId, request.currency(), request.amount()));
    }

    // --- Internal endpoints (called by other services; userId in request body) ---

    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse createAccount(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody CreateAccountRequest request
    ) {
        // We ignore any userId inside the request body and
        // strictly use the one provided in the 'X-User-Id' header.
        return toResponse(accountService.createAccount(userId, request.currency()));
    }

    @PostMapping("/funds/freeze")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void freezeFunds(@RequestBody FundsOperationRequest request) {
        accountService.freezeFunds(request.userId(), request.currency(), request.amount());
    }

    @PostMapping("/funds/unfreeze")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfreezeFunds(@RequestBody FundsOperationRequest request) {
        accountService.unfreezeFunds(request.userId(), request.currency(), request.amount());
    }

    @PostMapping("/funds/deduct/frozen")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deductFrozenFunds(@RequestBody FundsOperationRequest request) {
        accountService.deductFrozenFunds(request.userId(), request.currency(), request.amount());
    }

    @PostMapping("/funds/deduct")
    public void deductFunds(@RequestBody FundsOperationRequest request) {
        accountService.deductFunds(request.userId(), request.currency(), request.amount());
    }

    @PostMapping("/funds/withdraw")
    public AccountResponse withdraw(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody DepositRequest request // Using DepositRequest since it's just currency + amount
    ) {
        // We reuse the toResponse helper to send back the updated balance state
        return toResponse(accountService.deductFunds(userId, request.currency(), request.amount()));
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
