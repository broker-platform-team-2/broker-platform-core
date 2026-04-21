package lynx.team2.domain;

public class Holding extends Entity<Long>{
    private Long user_id;
    private InstrumentType instrument_type;
    private String instrument_id;
    private Double amount;
    private Double buying_price;
    private String currency;
    private Double average_cost;

    public Holding(Long user_id, InstrumentType instrument_type, String instrument_id, Double amount, Double buying_price, String currency) {
        this.user_id = user_id;
        this.instrument_type = instrument_type;
        this.instrument_id = instrument_id;
        this.amount = amount;
        this.buying_price = buying_price;
        this.currency = currency;
    }

    public Long getUser_id() {
        return user_id;
    }

    public void setUser_id(Long user_id) {
        this.user_id = user_id;
    }

    public InstrumentType getInstrument_type() {
        return instrument_type;
    }

    public void setInstrument_type(InstrumentType instrument_type) {
        this.instrument_type = instrument_type;
    }

    public String getInstrument_id() {
        return instrument_id;
    }

    public void setInstrument_id(String instrument_id) {
        this.instrument_id = instrument_id;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public Double getBuying_price() {
        return buying_price;
    }

    public void setBuying_price(Double buying_price) {
        this.buying_price = buying_price;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Double getAverage_cost() {
        return average_cost;
    }

    public void setAverage_cost(Double average_cost) {
        this.average_cost = average_cost;
    }
}
