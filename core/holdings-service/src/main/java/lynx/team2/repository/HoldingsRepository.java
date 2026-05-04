package lynx.team2.repository;

import lynx.team2.models.Holding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HoldingsRepository extends JpaRepository<Holding, Long> {

    List<Holding> findAllByUser_UserId(Long userId);

    List<Holding> findAllByUser_UserIdAndInstrumentId(Long userId, String instrumentId);
}
