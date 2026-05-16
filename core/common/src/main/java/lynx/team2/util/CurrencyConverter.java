package lynx.team2.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

public final class CurrencyConverter {

    private static final Map<String, BigDecimal> USD_RATES = Map.ofEntries(
            Map.entry("USD", new BigDecimal("1.0")),
            Map.entry("EUR", new BigDecimal("0.9259")),
            Map.entry("GBP", new BigDecimal("0.7963")),
            Map.entry("CHF", new BigDecimal("0.8981")),
            Map.entry("JPY", new BigDecimal("148.61")),
            Map.entry("CAD", new BigDecimal("1.3611")),
            Map.entry("AUD", new BigDecimal("1.5185")),
            Map.entry("NZD", new BigDecimal("1.6574")),
            Map.entry("SEK", new BigDecimal("10.463")),
            Map.entry("NOK", new BigDecimal("10.648")),
            Map.entry("DKK", new BigDecimal("6.907")),
            Map.entry("HKD", new BigDecimal("7.815")),
            Map.entry("SGD", new BigDecimal("1.352")),
            Map.entry("CNY", new BigDecimal("7.269")),
            Map.entry("INR", new BigDecimal("83.52")),
            Map.entry("KRW", new BigDecimal("1347.22")),
            Map.entry("BRL", new BigDecimal("5.019")),
            Map.entry("MXN", new BigDecimal("17.176")),
            Map.entry("ZAR", new BigDecimal("18.843")),
            Map.entry("TRY", new BigDecimal("32.593")),
            Map.entry("PLN", new BigDecimal("3.954")),
            Map.entry("RON", new BigDecimal("4.602"))
    );

    private CurrencyConverter() {}

    /** Convert a USD amount into the target currency. Returns the amount unchanged for unknown currencies. */
    public static BigDecimal fromUSD(BigDecimal usdAmount, String toCurrency) {
        BigDecimal rate = USD_RATES.getOrDefault(toCurrency, BigDecimal.ONE);
        return usdAmount.multiply(rate).setScale(6, RoundingMode.HALF_UP);
    }
}