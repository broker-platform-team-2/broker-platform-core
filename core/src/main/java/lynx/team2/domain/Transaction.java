package lynx.team2.domain;

import java.time.LocalDateTime;

public class Transaction extends Entity<Long>{
    private Long user_id;
    private Long exchange_order_id;
    private TransactionType type;
    private TransactionStatus status;
    private Double platform_fee;
    private Double price;
    private String currency;
    private Integer quantity;
    private LocalDateTime date;

    public Transaction(Long user_id, Long exchange_order_id, TransactionType type, TransactionStatus status, Double platform_fee, Double price, String currency, Integer quantity, LocalDateTime date) {
        this.user_id = user_id;
        this.exchange_order_id = exchange_order_id;
        this.type = type;
        this.status = status;
        this.platform_fee = platform_fee;
        this.price = price;
        this.currency = currency;
        this.quantity = quantity;
        this.date = date;
    }

    public Long getUser_id() {
        return user_id;
    }

    public void setUser_id(Long user_id) {
        this.user_id = user_id;
    }

    public Long getExchange_order_id() {
        return exchange_order_id;
    }

    public void setExchange_order_id(Long exchange_order_id) {
        this.exchange_order_id = exchange_order_id;
    }

    public TransactionType getType() {
        return type;
    }

    public void setType(TransactionType type) {
        this.type = type;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public void setStatus(TransactionStatus status) {
        this.status = status;
    }

    public Double getPlatform_fee() {
        return platform_fee;
    }

    public void setPlatform_fee(Double platform_fee) {
        this.platform_fee = platform_fee;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public LocalDateTime getDate() {
        return date;
    }

    public void setDate(LocalDateTime date) {
        this.date = date;
    }
}
