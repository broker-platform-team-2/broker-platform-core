package lynx.team2.repository;

import lynx.team2.models.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    public List<Transaction> findAllByUser_UserId(Long userId);

    public List<Transaction> findAllByUser_UserIdAndDate(Long userId, LocalDateTime date);

    public List<Transaction> findAllByUser_UserIdAndDateBetween(Long userId, LocalDateTime start, LocalDateTime end);

    public Optional<Transaction> findByExchangeOrderId(Long exchangeOrderId);
}
