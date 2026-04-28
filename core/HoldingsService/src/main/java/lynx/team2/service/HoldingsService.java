package lynx.team2.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lynx.team2.models.Holding;
import lynx.team2.repository.HoldingsRepository;
import lynx.team2.validators.HoldingsValidator;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class HoldingsService {
    private final HoldingsRepository repo;
    private final HoldingsValidator validator;

    @Transactional
    public Holding createHolding(Holding holding){
        validator.validate(holding);
        return repo.save(holding);
    }

    public void deleteHolding(Long id){
        repo.deleteById(id);
    }

    @Transactional
    public Holding updateHolding(Holding holding){
        validator.validate(holding);
        return repo.save(holding);
    }

    public List<Holding> findAllForUserId(Long id){
        return repo.findAllByUser_Id(id);
    }

    public List<Holding> findAllForUserIdAndInstrumentId(Long userId,Long instrumentId){
        return repo.findAllByUser_IdAndInstrumentId(userId,instrumentId);
    }
}
