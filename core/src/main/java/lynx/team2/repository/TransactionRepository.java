package lynx.team2.repository;

import lynx.team2.domain.Transaction;

import java.time.LocalDateTime;
import java.util.List;

public interface TransactionRepository extends RepoInterface<Long, Transaction>{
    public List<Transaction> findAllByUserId(Long userId);
    public List<Transaction> findAllByUserIdAndDate(Long userId, LocalDateTime date);
}
