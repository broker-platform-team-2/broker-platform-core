package lynx.team2.repository;

import lynx.team2.models.AccountFundsOperation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AccountFundsOperationRepository extends JpaRepository<AccountFundsOperation, Long> {

    List<AccountFundsOperation> findAllByAccountAccountIdOrderByDateDesc(Long accountId);
}
