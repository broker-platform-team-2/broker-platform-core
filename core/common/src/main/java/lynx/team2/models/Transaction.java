package lynx.team2.models;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="transaction_id")
    private Long transactionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id",nullable=false)
    private User user;

    @Column(name="exchange_order_id",unique = true)
    private Long exchangeOrderId;

    @Enumerated(EnumType.STRING)
    @Column(name="type",nullable = false)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(name="status",nullable = false)
    private TransactionStatus status;

    @Column(name="platform_fee",nullable = false)
    private BigDecimal platformFee;

    @Column(name="price",nullable = false)
    private BigDecimal price;

    @Column(name="currency",nullable = false,length = 3)
    private String currency;

    @Column(name="quantity",nullable = false)
    private Integer quantity;

    @Column(name="date")
    private LocalDateTime date;


    // Automatically set the timestamp when the record is created
    @PrePersist
    protected void onCreate(){
        this.date = LocalDateTime.now();
    }


}
