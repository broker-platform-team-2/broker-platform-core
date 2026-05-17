package lynx.team2.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lynx.team2.models.Transaction;
import lynx.team2.models.TransactionStatus;
import lynx.team2.repository.TransactionRepository;
import lynx.team2.validators.TransactionValidator;
import org.springframework.stereotype.Service;


import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TransactionService {
    private final TransactionRepository repo;
    private final TransactionValidator validator;

    @Transactional
    public Transaction createTransaction(Transaction transaction) {
        validator.validate(transaction);
        return repo.save(transaction);
    }

    public Optional<Transaction> findById(Long id) {
        return repo.findById(id);
    }

    public void deleteTransaction(Long id){
        repo.deleteById(id);
    }

    public List<Transaction> findAllForUserId(Long userId){
        return repo.findAllByUser_UserId(userId);
    }

    public List<Transaction> findAllForUserIdAndDate(Long userId, LocalDateTime date){
        return repo.findAllByUser_UserIdAndDate(userId, date);
    }

    public List<Transaction> findAllForUserIdAndDateRange(Long userId, LocalDateTime startDate, LocalDateTime endDate){
        return repo.findAllByUser_UserIdAndDateBetween(userId, startDate, endDate);
    }

    public Optional<Transaction> findByExchangeOrderId(String exchangeOrderId) {
        return repo.findByExchangeOrderId(exchangeOrderId);
    }

    public List<Transaction> findAllByStatus(TransactionStatus status) {
        return repo.findAllByStatus(status);
    }

    @Transactional
    public Transaction updateStatus(String exchangeOrderId, TransactionStatus status) {
        Transaction t = repo.findByExchangeOrderId(exchangeOrderId)
                .orElseThrow(() -> new RuntimeException("Transaction not found for exchangeOrderId=" + exchangeOrderId));
        t.setStatus(status);
        return repo.save(t);
    }

    @Transactional
    public Transaction save(Transaction transaction) {
        return repo.save(transaction);
    }

}
