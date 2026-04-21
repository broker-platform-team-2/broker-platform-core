package lynx.team2.repository;

import lynx.team2.domain.Transaction;
import lynx.team2.repository.Abstract.TransactionRepository;

import java.time.LocalDateTime;
import java.util.List;

public class TransactionDBRepository implements TransactionRepository {

    @Override
    public List<Transaction> findAllByUserId(Long userId) {
        return List.of();
    }

    @Override
    public List<Transaction> findAllByUserIdAndDate(Long userId, LocalDateTime date) {
        return List.of();
    }

    @Override
    public void add(Transaction entity) {

    }

    @Override
    public void update(Transaction entity) {

    }

    @Override
    public void delete(Long aLong) {

    }

    @Override
    public Transaction findOne(Long aLong) {
        return null;
    }

    @Override
    public List<Transaction> findAll() {
        return List.of();
    }
}
