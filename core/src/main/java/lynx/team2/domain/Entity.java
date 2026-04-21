package lynx.team2.domain;

import java.io.Serializable;

public abstract class Entity<ID> implements Serializable {
    private static final long serialVersionUID = 1L;
    protected ID id;


    public ID getId() {
        return id;
    }

    public void setId(ID id) {
        this.id = id;
    }
}
