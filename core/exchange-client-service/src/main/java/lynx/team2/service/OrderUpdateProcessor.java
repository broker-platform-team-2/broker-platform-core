package lynx.team2.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lynx.team2.client.AccountServiceClient;
import lynx.team2.client.HoldingsServiceClient;
import lynx.team2.client.NotificationServiceClient;
import lynx.team2.client.TransactionServiceClient;
import lynx.team2.dto.NotificationMessage;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderUpdateProcessor {

    private final ObjectMapper objectMapper;
    private final NotificationServiceClient notificationClient;
    private final TransactionServiceClient transactionServiceClient;
    private final AccountServiceClient accountServiceClient;
    private final HoldingsServiceClient holdingsServiceClient;

    private static final String TRADE_CURRENCY = "EUR";

    public void process(JsonNode payload) {
        String orderId = payload.path("order_id").asText();
        String status = payload.path("status").asText();
        log.info("ORDER_UPDATE received: order_id={} status={}", orderId, status);

        notificationClient.broadcast(new NotificationMessage(
                "ORDER_UPDATE",
                objectMapper.convertValue(payload, Map.class)
        ));

        if (orderId == null || orderId.isBlank()) {
            log.warn("Missing order_id on ORDER_UPDATE payload; cannot settle");
            return;
        }

        TransactionServiceClient.TransactionDto tx;
        try {
            tx = transactionServiceClient.findByExchangeOrderId(orderId);
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("No transaction found for exchangeOrderId={}, skipping settlement", orderId);
            return;
        } catch (Exception e) {
            log.error("Failed to look up transaction for exchangeOrderId={}", orderId, e);
            return;
        }

        switch (status) {
            case "FILLED" -> settle(tx, payload, "FILLED");
            case "PARTIALLY_FILLED" -> settlePartial(tx, payload);
            case "CANCELED" -> cancel(tx);
            case "EXPIRED" -> cancel(tx);
            case "REJECTED" -> cancel(tx);
            default -> log.debug("No settlement action for status={}", status);
        }
    }

    private void settle(TransactionServiceClient.TransactionDto tx, JsonNode payload, String newStatus) {
        int filledQty = payload.path("filled_quantity").asInt(tx.quantity());
        BigDecimal avgPrice = new BigDecimal(payload.path("average_fill_price")
                .asText(tx.price() != null ? tx.price().toPlainString() : "0"));
        String instrumentId = tx.instrumentId() != null ? tx.instrumentId() : "";
        String instrumentType = tx.instrumentType() != null ? tx.instrumentType() : "STOCK";
        boolean isBuy = "BUY".equalsIgnoreCase(tx.type());

        BigDecimal settledAmount = avgPrice.multiply(BigDecimal.valueOf(filledQty));

        try {
            if (isBuy) {
                accountServiceClient.deductFrozenFunds(tx.userId(), TRADE_CURRENCY, settledAmount);
                if (!instrumentId.isBlank()) {
                    holdingsServiceClient.upsertHolding(
                            tx.userId(), instrumentType, instrumentId,
                            BigDecimal.valueOf(filledQty), TRADE_CURRENCY, avgPrice);
                }
            } else {
                accountServiceClient.depositFunds(tx.userId(), TRADE_CURRENCY, settledAmount);
                if (!instrumentId.isBlank()) {
                    holdingsServiceClient.reduceHolding(tx.userId(), instrumentId, BigDecimal.valueOf(filledQty));
                }
            }
            transactionServiceClient.updateStatus(tx.exchangeOrderId(), newStatus);
            log.info("Settled {} order {} userId={} amount={}", isBuy ? "BUY" : "SELL", tx.exchangeOrderId(), tx.userId(), settledAmount);
        } catch (Exception e) {
            log.error("Settlement failed for exchangeOrderId={}", tx.exchangeOrderId(), e);
        }
    }

    private void settlePartial(TransactionServiceClient.TransactionDto tx, JsonNode payload) {
        try {
            transactionServiceClient.updateStatus(tx.exchangeOrderId(), "PARTIALLY_FILLED");
        } catch (Exception e) {
            log.error("Failed to update partial fill status for exchangeOrderId={}", tx.exchangeOrderId(), e);
        }
    }

    private void cancel(TransactionServiceClient.TransactionDto tx) {
        boolean isBuy = "BUY".equalsIgnoreCase(tx.type());
        try {
            if (isBuy && tx.price() != null && tx.quantity() != null) {
                BigDecimal frozenEstimate = tx.price().multiply(BigDecimal.valueOf(tx.quantity()));
                accountServiceClient.unfreezeFunds(tx.userId(), TRADE_CURRENCY, frozenEstimate);
            }
            transactionServiceClient.updateStatus(tx.exchangeOrderId(), "CANCELED");
            log.info("Canceled order {} userId={}", tx.exchangeOrderId(), tx.userId());
        } catch (Exception e) {
            log.error("Failed to cancel order exchangeOrderId={}", tx.exchangeOrderId(), e);
        }
    }

}
