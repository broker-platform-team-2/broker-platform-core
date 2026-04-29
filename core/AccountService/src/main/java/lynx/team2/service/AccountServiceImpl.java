package lynx.team2.service;

import lynx.team2.exceptions.RepoException;
import lynx.team2.exceptions.ValidatorException;
import lynx.team2.models.Account;
import lynx.team2.models.User;
import lynx.team2.repository.AccountRepository;
import lynx.team2.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public Account createAccount(Long userId, String currency) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RepoException("User not found"));

        Account account = new Account();
        account.setUser(user);
        account.setBalance(BigDecimal.ZERO);
        account.setFrozenBalance(BigDecimal.ZERO);
        account.setCurrency(currency != null ? currency : "USD");

        return accountRepository.save(account);
    }

    @Override
    @Transactional(readOnly = true)
    public Account getAccountByUserId(Long userId) {
        return accountRepository.findByUserUserId(userId)
                .orElseThrow(() -> new RepoException("Account not found for user ID: " + userId));
    }

    @Override
    @Transactional
    public Account depositFunds(Long userId, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidatorException("Deposit amount must be positive");
        }

        Account account = accountRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new RepoException("Account not found"));

        account.setBalance(account.getBalance().add(amount));
        return accountRepository.save(account);
    }

    @Override
    @Transactional
    public void freezeFunds(Long userId, BigDecimal amount) {
        Account account = accountRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new RepoException("Account not found"));

        if (account.getBalance().compareTo(amount) < 0) {
            throw new ValidatorException("Insufficient funds to block for order");
        }

        account.setBalance(account.getBalance().subtract(amount));
        account.setFrozenBalance(account.getFrozenBalance().add(amount));
        accountRepository.save(account);
    }

    @Override
    @Transactional
    public void unfreezeFunds(Long userId, BigDecimal amount) {
        Account account = accountRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new RepoException("Account not found"));

        BigDecimal amountToUnfreeze = amount.min(account.getFrozenBalance());

        account.setFrozenBalance(account.getFrozenBalance().subtract(amountToUnfreeze));
        account.setBalance(account.getBalance().add(amountToUnfreeze));
        accountRepository.save(account);
    }

    @Override
    @Transactional
    public void deductFrozenFunds(Long userId, BigDecimal totalCostWithFees) {
        Account account = accountRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new RepoException("Account not found"));

        if (account.getFrozenBalance().compareTo(totalCostWithFees) < 0) {
            BigDecimal deficit = totalCostWithFees.subtract(account.getFrozenBalance());
            account.setFrozenBalance(BigDecimal.ZERO);
            account.setBalance(account.getBalance().subtract(deficit));
        } else {
            account.setFrozenBalance(account.getFrozenBalance().subtract(totalCostWithFees));
        }

        accountRepository.save(account);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getAvailableBalance(Long userId) {
        return getAccountByUserId(userId).getBalance();
    }

    @Override
    @Transactional(readOnly = true)
    public String getAccountCurrency(Long userId) {
        return getAccountByUserId(userId).getCurrency();
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getTotalEquity(Long userId) {
        Account acc = getAccountByUserId(userId);
        return acc.getBalance().add(acc.getFrozenBalance());
    }
}