package lynx.team2.validators;

import lynx.team2.exceptions.ValidatorException;
import lynx.team2.models.Holding;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class HoldingsValidator {

    public void validate(Holding holding) {
        if (holding.getInstrumentType() == null) {
            throw new ValidatorException("Instrument type must not be null");
        }
        if (holding.getInstrumentId() == null || holding.getInstrumentId().isBlank()) {
            throw new ValidatorException("Instrument ID must not be blank");
        }
        if (holding.getAmount() == null || holding.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidatorException("Holding amount must be greater than zero");
        }
        if (holding.getCurrency() == null || holding.getCurrency().isBlank()) {
            throw new ValidatorException("Holding currency must not be blank");
        }
        if (holding.getCurrency().length() != 3) {
            throw new ValidatorException("Holding currency must be a 3-character ISO code");
        }
        if (holding.getAverageCost() == null || holding.getAverageCost().compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidatorException("Holding average cost must be zero or positive");
        }
        if (holding.getUser() == null) {
            throw new ValidatorException("Holding must be associated with a user");
        }
    }
}
