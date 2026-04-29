package lynx.team2.service;

import lynx.team2.models.Account;

import java.math.BigDecimal;

public interface AccountService {
    Account createAccount(Long userId, String currency);
    Account getAccountByUserId(Long userId);
    Account depositFunds(Long userId, BigDecimal amount);

    // Logica de Trading
    void freezeFunds(Long userId, BigDecimal amount);
    void unfreezeFunds(Long userId, BigDecimal amount);
    void deductFrozenFunds(Long userId, BigDecimal totalCostWithFees);

    // Helpers pentru UI/HomePageController
    BigDecimal getAvailableBalance(Long userId);
    String getAccountCurrency(Long userId);
    BigDecimal getTotalEquity(Long userId);
}
