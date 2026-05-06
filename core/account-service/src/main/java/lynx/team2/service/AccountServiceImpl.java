package lynx.team2.service;

import lynx.team2.exceptions.RepoException;
import lynx.team2.exceptions.ValidatorException;
import lynx.team2.models.Account;
import lynx.team2.models.AccountFundsOperation;
import lynx.team2.models.FundsOperationType;
import lynx.team2.models.User;
import lynx.team2.repository.AccountFundsOperationRepository;
import lynx.team2.repository.AccountRepository;
import lynx.team2.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final AccountFundsOperationRepository fundsOperationRepository;

    @Override
    @Transactional
    public Account createAccount(Long userId, String currency) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RepoException("User not found"));

        String normalized = currency != null ? currency.toUpperCase() : "EUR";

        // Idempotent — if the user already has an account in this currency, return it.
        return accountRepository.findByUserUserIdAndCurrency(userId, normalized)
                .orElseGet(() -> {
                    Account account = new Account();
                    account.setUser(user);
                    account.setBalance(BigDecimal.ZERO);
                    account.setFrozenBalance(BigDecimal.ZERO);
                    account.setCurrency(normalized);
                    return accountRepository.save(account);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public List<Account> getAccountsByUserId(Long userId) {
        return accountRepository.findAllByUserUserIdOrderByAccountIdAsc(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Account getAccountByUserIdAndCurrency(Long userId, String currency) {
        return accountRepository.findByUserUserIdAndCurrency(userId, currency.toUpperCase())
                .orElseThrow(() -> new RepoException(
                        "Account not found for user " + userId + " in " + currency));
    }

    @Override
    @Transactional
    public Account depositFunds(Long userId, String currency, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidatorException("Deposit amount must be positive");
        }

        Account account = lockAccount(userId, currency);
        account.setBalance(account.getBalance().add(amount));
        Account saved = accountRepository.save(account);
        fundsOperationRepository.save(AccountFundsOperation.builder()
                .account(saved)
                .operationType(FundsOperationType.DEPOSIT)
                .amount(amount)
                .build());
        return saved;
    }

    @Override
    @Transactional
    public Account deductFunds(Long userId, String currency, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidatorException("Deduct amount must be positive");
        }

        Account account = lockAccount(userId, currency);
        if(account.getBalance().subtract(amount).compareTo(BigDecimal.ZERO) <= 0){
            throw new ValidatorException("Insufficient funds");
        }
        account.setBalance(account.getBalance().subtract(amount));
        Account saved = accountRepository.save(account);
        fundsOperationRepository.save(AccountFundsOperation.builder()
                .account(saved)
                .operationType(FundsOperationType.WITHDRAW)
                .amount(amount)
                .build());
        return saved;
    }

    @Override
    @Transactional
    public void freezeFunds(Long userId, String currency, BigDecimal amount) {
        Account account = lockAccount(userId, currency);

        if (account.getBalance().compareTo(amount) < 0) {
            throw new ValidatorException("Insufficient funds to block for order");
        }

        account.setBalance(account.getBalance().subtract(amount));
        account.setFrozenBalance(account.getFrozenBalance().add(amount));
        accountRepository.save(account);
    }

    @Override
    @Transactional
    public void unfreezeFunds(Long userId, String currency, BigDecimal amount) {
        Account account = lockAccount(userId, currency);

        BigDecimal amountToUnfreeze = amount.min(account.getFrozenBalance());

        account.setFrozenBalance(account.getFrozenBalance().subtract(amountToUnfreeze));
        account.setBalance(account.getBalance().add(amountToUnfreeze));
        accountRepository.save(account);
    }

    @Override
    @Transactional
    public void deductFrozenFunds(Long userId, String currency, BigDecimal totalCostWithFees) {
        Account account = lockAccount(userId, currency);

        if (account.getFrozenBalance().compareTo(totalCostWithFees) < 0) {
            BigDecimal deficit = totalCostWithFees.subtract(account.getFrozenBalance());
            account.setFrozenBalance(BigDecimal.ZERO);
            account.setBalance(account.getBalance().subtract(deficit));
        } else {
            account.setFrozenBalance(account.getFrozenBalance().subtract(totalCostWithFees));
        }

        accountRepository.save(account);
    }

    private Account lockAccount(Long userId, String currency) {
        return accountRepository.findByUserIdAndCurrencyWithLock(userId, currency.toUpperCase())
                .orElseThrow(() -> new RepoException(
                        "Account not found for user " + userId + " in " + currency));
    }
}
