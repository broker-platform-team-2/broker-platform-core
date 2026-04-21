package lynx.team2.service;

import lynx.team2.domain.Transaction;
import lynx.team2.repository.TransactionRepository;
import lynx.team2.validator.TransactionValidator;

import java.time.LocalDateTime;
import java.util.List;

public class TransactionService {
    private TransactionValidator validator;
    private TransactionRepository repo;

    public TransactionService(TransactionValidator transactionValidator, TransactionRepository transactionRepository) {
        this.validator = transactionValidator;
        this.repo = transactionRepository;
    }

    public void add(Transaction entity) {
        validator.validate(entity);
        repo.add(entity);
    }

    public void delete(Long entity_id) {
        repo.delete(entity_id);
    }

    public void update(Transaction entity) {
        validator.validate(entity);
        repo.update(entity);
    }

    public List<Transaction> findAllForUserId(Long userId) {
        return repo.findAllByUserId(userId);
    }

    public List<Transaction> findAllForUserIdAndDate(Long userId, LocalDateTime date) {
        return repo.findAllByUserIdAndDate(userId, date);
    }
}
