package lynx.team2.service;

import lynx.team2.models.Account;
import lynx.team2.models.AccountFundsOperation;

import java.math.BigDecimal;
import java.util.List;

public interface AccountService {
    Account createAccount(Long userId, String currency);

    /** All accounts for a user, oldest first (primary). */
    List<Account> getAccountsByUserId(Long userId);

    /** Look up the user's account in the given currency. */
    Account getAccountByUserIdAndCurrency(Long userId, String currency);

    /** Add cash to the user's account in the given currency. */
    Account depositFunds(Long userId, String currency, BigDecimal amount);

    // --- Trading flows (currency-scoped) ---
    void freezeFunds(Long userId, String currency, BigDecimal amount);
    void unfreezeFunds(Long userId, String currency, BigDecimal amount);
    void deductFrozenFunds(Long userId, String currency, BigDecimal totalCostWithFees);

    /** Withdraw cash from user's account in the given currency. */
    Account deductFunds(Long userId, String currency, BigDecimal amount);

    /** All deposit/withdraw operations for the user's account in the given currency, newest first. */
    List<AccountFundsOperation> getFundOperations(Long userId, String currency);
}
