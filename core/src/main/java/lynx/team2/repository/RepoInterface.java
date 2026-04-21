package lynx.team2.repository;

import lynx.team2.domain.Entity;

import java.util.List;

public interface RepoInterface<ID,T extends Entity<ID>> {
    public void add(T entity);
    public void update(T entity);
    public void delete(ID id);
    public T findOne(ID id);
    public List<T> findAll();
}
