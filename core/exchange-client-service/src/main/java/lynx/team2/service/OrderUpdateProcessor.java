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
import lynx.team2.util.CurrencyConverter;
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

    public void process(JsonNode payload) {
        String orderId = payload.path("order_id").asText();
        String status = payload.path("status").asText();
        log.info("ORDER_UPDATE received: order_id={} status={}", orderId, status);

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

        // Skip if already in a terminal state (prevents double-settlement from polling + WebSocket)
        String currentStatus = tx.status() != null ? tx.status().toUpperCase() : "";
        if (!currentStatus.equals("PENDING") && !currentStatus.equals("PARTIALLY_FILLED")) {
            log.debug("Transaction {} already in status {}, skipping", orderId, currentStatus);
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

    private void broadcastUpdate(JsonNode payload) {
        try {
            notificationClient.broadcast(new NotificationMessage(
                    "ORDER_UPDATE",
                    objectMapper.convertValue(payload, Map.class)
            ));
        } catch (Exception e) {
            log.warn("Failed to broadcast ORDER_UPDATE notification: {}", e.getMessage());
        }
    }

    private void settle(TransactionServiceClient.TransactionDto tx, JsonNode payload, String newStatus) {
        int filledQty = payload.path("filled_quantity").asInt(tx.quantity());
        BigDecimal avgPriceUSD = new BigDecimal(payload.path("average_fill_price")
                .asText(tx.price() != null ? tx.price().toPlainString() : "0"));
        String instrumentId = tx.instrumentId() != null ? tx.instrumentId() : "";
        String instrumentType = tx.instrumentType() != null ? tx.instrumentType() : "STOCK";
        boolean isBuy = "BUY".equalsIgnoreCase(tx.type());

        String accountCurrency = (tx.currency() != null && !tx.currency().isBlank()) ? tx.currency() : "USD";
        BigDecimal settledAmountUSD = avgPriceUSD.multiply(BigDecimal.valueOf(filledQty));
        BigDecimal settledAmount = CurrencyConverter.fromUSD(settledAmountUSD, accountCurrency);

        // Step 1: settle funds — abort entirely if this fails (funds state is unknown)
        try {
            if (isBuy) {
                accountServiceClient.deductFrozenFunds(tx.userId(), accountCurrency, settledAmount);

                // Release any excess frozen funds (buffer from MARKET order freeze at price * 1.05)
                if (tx.price() != null && tx.quantity() != null) {
                    BigDecimal frozenEstimateUSD = tx.price().multiply(BigDecimal.valueOf(tx.quantity()));
                    BigDecimal frozenEstimate = CurrencyConverter.fromUSD(frozenEstimateUSD, accountCurrency);
                    BigDecimal excess = frozenEstimate.subtract(settledAmount);
                    if (excess.compareTo(BigDecimal.ZERO) > 0) {
                        try {
                            accountServiceClient.unfreezeFunds(tx.userId(), accountCurrency, excess);
                            log.info("Released excess frozen funds={} {} for order={}",
                                    excess, accountCurrency, tx.exchangeOrderId());
                        } catch (Exception ex) {
                            log.warn("Could not release excess frozen funds for order={}: {}",
                                    tx.exchangeOrderId(), ex.getMessage());
                        }
                    }
                }
            } else {
                accountServiceClient.depositFunds(tx.userId(), accountCurrency, settledAmount);
            }
        } catch (Exception e) {
            log.error("Fund settlement failed for exchangeOrderId={} — aborting", tx.exchangeOrderId(), e);
            return;
        }

        // Step 2: update holdings — cost basis stored in USD (exchange native currency)
        if (!instrumentId.isBlank()) {
            try {
                if (isBuy) {
                    holdingsServiceClient.upsertHolding(
                            tx.userId(), instrumentType, instrumentId,
                            BigDecimal.valueOf(filledQty), "USD", avgPriceUSD);
                } else {
                    holdingsServiceClient.reduceHolding(tx.userId(), instrumentId, BigDecimal.valueOf(filledQty));
                }
            } catch (Exception e) {
                log.error("Holdings update failed for exchangeOrderId={} — continuing to mark as {}",
                        tx.exchangeOrderId(), newStatus, e);
            }
        }

        // Step 3: mark transaction settled — always reached as long as funds settled
        try {
            transactionServiceClient.updateStatus(tx.exchangeOrderId(), newStatus);
            log.info("Settled {} order {} userId={} amount={}", isBuy ? "BUY" : "SELL",
                    tx.exchangeOrderId(), tx.userId(), settledAmount);
        } catch (Exception e) {
            log.error("Failed to mark exchangeOrderId={} as {}", tx.exchangeOrderId(), newStatus, e);
            return;
        }

        // Step 4: notify UI only after the DB is updated
        broadcastUpdate(payload);
    }

    private void settlePartial(TransactionServiceClient.TransactionDto tx, JsonNode payload) {
        try {
            transactionServiceClient.updateStatus(tx.exchangeOrderId(), "PARTIALLY_FILLED");
        } catch (Exception e) {
            log.error("Failed to update partial fill status for exchangeOrderId={}", tx.exchangeOrderId(), e);
            return;
        }
        broadcastUpdate(payload);
    }

    private void cancel(TransactionServiceClient.TransactionDto tx) {
        boolean isBuy = "BUY".equalsIgnoreCase(tx.type());
        String accountCurrency = (tx.currency() != null && !tx.currency().isBlank()) ? tx.currency() : "USD";
        try {
            if (isBuy && tx.price() != null && tx.quantity() != null) {
                BigDecimal frozenEstimateUSD = tx.price().multiply(BigDecimal.valueOf(tx.quantity()));
                BigDecimal frozenEstimate = CurrencyConverter.fromUSD(frozenEstimateUSD, accountCurrency);
                accountServiceClient.unfreezeFunds(tx.userId(), accountCurrency, frozenEstimate);
            }
            transactionServiceClient.updateStatus(tx.exchangeOrderId(), "CANCELED");
            log.info("Canceled order {} userId={}", tx.exchangeOrderId(), tx.userId());
        } catch (Exception e) {
            log.error("Failed to cancel order exchangeOrderId={}", tx.exchangeOrderId(), e);
            return;
        }
        // Reuse the tx fields to build a minimal notification payload
        try {
            notificationClient.broadcast(new NotificationMessage(
                    "ORDER_UPDATE",
                    Map.of("order_id", tx.exchangeOrderId(), "status", "CANCELED")
            ));
        } catch (Exception e) {
            log.warn("Failed to broadcast CANCELED notification for exchangeOrderId={}: {}",
                    tx.exchangeOrderId(), e.getMessage());
        }
    }

}
