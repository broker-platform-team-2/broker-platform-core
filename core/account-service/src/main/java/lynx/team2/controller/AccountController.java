package lynx.team2.controller;

import lynx.team2.dto.AccountResponse;
import lynx.team2.dto.CreateAccountRequest;
import lynx.team2.dto.DepositRequest;
import lynx.team2.dto.FundsOperationRequest;
import lynx.team2.dto.FundsOperationResponse;
import lynx.team2.models.Account;
import lynx.team2.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @Value("${internal.token}")
    private String internalToken;

    // --- User-facing endpoints (userId injected by gateway as X-User-Id header) ---

    /** All accounts the caller owns, primary first. The frontend computes "available" per currency. */
    @GetMapping("/accounts/me")
    public List<AccountResponse> getMyAccounts(@RequestHeader("X-User-Id") Long userId) {
        return accountService.getAccountsByUserId(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    /** Deposit/withdraw history for the caller's account in a given currency, newest first. */
    @GetMapping("/funds/history")
    public List<FundsOperationResponse> getFundHistory(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam String currency
    ) {
        return accountService.getFundOperations(userId, currency).stream()
                .map(op -> new FundsOperationResponse(
                        op.getOperationId(),
                        op.getOperationType(),
                        op.getAmount(),
                        op.getAccount().getCurrency(),
                        op.getDate()
                ))
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
    public void freezeFunds(
            @RequestHeader("X-Internal-Token") String token,
            @RequestBody FundsOperationRequest request) {
        verifyInternalToken(token);
        accountService.freezeFunds(request.userId(), request.currency(), request.amount());
    }

    @PostMapping("/funds/unfreeze")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfreezeFunds(
            @RequestHeader("X-Internal-Token") String token,
            @RequestBody FundsOperationRequest request) {
        verifyInternalToken(token);
        accountService.unfreezeFunds(request.userId(), request.currency(), request.amount());
    }

    @PostMapping("/funds/deduct/frozen")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deductFrozenFunds(
            @RequestHeader("X-Internal-Token") String token,
            @RequestBody FundsOperationRequest request) {
        verifyInternalToken(token);
        accountService.deductFrozenFunds(request.userId(), request.currency(), request.amount());
    }

    @PostMapping("/funds/deduct")
    public void deductFunds(
            @RequestHeader("X-Internal-Token") String token,
            @RequestBody FundsOperationRequest request) {
        verifyInternalToken(token);
        accountService.deductFunds(request.userId(), request.currency(), request.amount());
    }

    private void verifyInternalToken(String token) {
        if (!internalToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
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
