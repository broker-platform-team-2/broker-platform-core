package lynx.team2.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import lynx.team2.models.Account;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    /** All accounts for a user, ordered by accountId so the oldest is "primary." */
    List<Account> findAllByUserUserIdOrderByAccountIdAsc(Long userId);

    /** A specific currency account for a user. */
    Optional<Account> findByUserUserIdAndCurrency(Long userId, String currency);

    /** A specific currency account, with a row-level write lock for concurrent updates. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.user.userId = :userId AND a.currency = :currency")
    Optional<Account> findByUserIdAndCurrencyWithLock(
            @Param("userId") Long userId,
            @Param("currency") String currency
    );
}
