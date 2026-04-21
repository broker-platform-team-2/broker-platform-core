package lynx.team2.domain;

public class Account extends Entity<Long>{
    private Long user_id;
    private Double balance;
    private Double frozen_balance;
    private String currency;

    public Account(Long user_id, Double balance, Double frozen_balance, String currency) {
        this.user_id = user_id;
        this.balance = balance;
        this.frozen_balance = frozen_balance;
        this.currency = currency;
    }

    public Account(Long user_id, Double balance, String currency) {
        this.user_id = user_id;
        this.balance = balance;
        this.currency = currency;
        this.frozen_balance = 0.0;
    }

    public Long getUser_id() {
        return user_id;
    }

    public void setUser_id(Long user_id) {
        this.user_id = user_id;
    }

    public Double getBalance() {
        return balance;
    }

    public void setBalance(Double balance) {
        this.balance = balance;
    }

    public Double getFrozen_balance() {
        return frozen_balance;
    }

    public void setFrozen_balance(Double frozen_balance) {
        this.frozen_balance = frozen_balance;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
}
