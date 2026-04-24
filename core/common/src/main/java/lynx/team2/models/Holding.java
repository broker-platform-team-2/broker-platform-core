package lynx.team2.models;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "holdings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Holding{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="holding_id")
    private Long holdingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name="instrument_type",nullable = false)
    private InstrumentType instrumentType;

    @Column(name="instrument_id",nullable = false)
    private String instrumentId;

    @Column(name="amount",nullable = false)
    private BigDecimal amount;

    @Column(name="buying_price",nullable = false)
    private BigDecimal buyingPrice;

    @Column(name="currency",nullable = false, length = 3)
    private String currency;

    @Column(name="average_cost",nullable = false)
    private BigDecimal averageCost;

}
