package lynx.team2.validators;

import lynx.team2.exceptions.ValidatorException;
import lynx.team2.models.Transaction;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class TransactionValidator {

    public void validate(Transaction transaction) {
        if (transaction.getType() == null) {
            throw new ValidatorException("Transaction type must not be null");
        }
        if (transaction.getStatus() == null) {
            throw new ValidatorException("Transaction status must not be null");
        }
        if (transaction.getPrice() == null || transaction.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidatorException("Transaction price must be greater than zero");
        }
        if (transaction.getQuantity() == null || transaction.getQuantity() <= 0) {
            throw new ValidatorException("Transaction quantity must be greater than zero");
        }
        if (transaction.getCurrency() == null || transaction.getCurrency().isBlank()) {
            throw new ValidatorException("Transaction currency must not be blank");
        }
        if (transaction.getCurrency().length() != 3) {
            throw new ValidatorException("Transaction currency must be a 3-character ISO code");
        }
        if (transaction.getPlatformFee() == null || transaction.getPlatformFee().compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidatorException("Transaction platform fee must be zero or positive");
        }
        if (transaction.getUser() == null) {
            throw new ValidatorException("Transaction must be associated with a user");
        }
    }
}
