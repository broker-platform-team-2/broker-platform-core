package lynx.team2.validator;

import lynx.team2.domain.Entity;

public interface Validator <ID,T extends Entity<ID>>{
    public void validate(T entity);
}
