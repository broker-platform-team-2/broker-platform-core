package lynx.team2.repository;

import lynx.team2.models.Holding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HoldingsRepository extends JpaRepository<Holding,Long> {

    public Holding updateHolding(Holding holding);

    public List<Holding> findAllByUser_Id(Long id);

    public List<Holding> findAllByUser_IdAndInstrumentId(Long id, Long instrumentId);
}
