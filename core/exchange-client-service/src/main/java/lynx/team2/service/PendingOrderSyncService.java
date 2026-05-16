package lynx.team2.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lynx.team2.client.TransactionServiceClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;

@Slf4j
@Component
public class PendingOrderSyncService {

    private final TransactionServiceClient transactionServiceClient;
    private final OrderUpdateProcessor orderUpdateProcessor;
    private final ObjectMapper objectMapper;
    private final RestClient exchangeRestClient;

    public PendingOrderSyncService(
            TransactionServiceClient transactionServiceClient,
            OrderUpdateProcessor orderUpdateProcessor,
            ObjectMapper objectMapper,
            @Qualifier("exchangeRestClient") RestClient exchangeRestClient) {
        this.transactionServiceClient = transactionServiceClient;
        this.orderUpdateProcessor = orderUpdateProcessor;
        this.objectMapper = objectMapper;
        this.exchangeRestClient = exchangeRestClient;
    }

    @Scheduled(fixedDelayString = "${exchange.sync-delay-ms:15000}")
    public void syncPendingOrders() {
        List<TransactionServiceClient.TransactionDto> pending;
        try {
            pending = transactionServiceClient.findAllPending();
        } catch (Exception e) {
            log.warn("Pending order sync: could not fetch pending transactions: {}", e.getMessage());
            return;
        }

        if (pending == null || pending.isEmpty()) return;

        log.info("Pending order sync: checking {} transaction(s) against exchange", pending.size());

        for (TransactionServiceClient.TransactionDto tx : pending) {
            if (tx.exchangeOrderId() == null || tx.exchangeOrderId().isBlank()) continue;
            try {
                syncOrder(tx);
            } catch (Exception e) {
                log.warn("Pending order sync: error syncing order {}: {}", tx.exchangeOrderId(), e.getMessage());
            }
        }
    }

    private void syncOrder(TransactionServiceClient.TransactionDto tx) {
        String raw;
        try {
            raw = exchangeRestClient.get()
                    .uri("/orders/{id}", tx.exchangeOrderId())
                    .retrieve()
                    .body(String.class);
        } catch (HttpClientErrorException.NotFound e) {
            log.debug("Pending order sync: order {} not found in exchange", tx.exchangeOrderId());
            return;
        }

        if (raw == null) return;

        JsonNode orderNode;
        try {
            orderNode = objectMapper.readTree(raw);
        } catch (Exception e) {
            log.warn("Pending order sync: could not parse exchange response for {}: {}", tx.exchangeOrderId(), e.getMessage());
            return;
        }

        String exchangeStatus = orderNode.path("status").asText("").toUpperCase();

        // Options are not auto-filled by the exchange matching engine.
        // Auto-settle any PENDING OPTION order at the premium recorded at placement time.
        if ((exchangeStatus.isBlank() || exchangeStatus.equals("PENDING"))
                && "OPTION".equalsIgnoreCase(tx.instrumentType())) {
            log.info("Pending order sync: auto-settling OPTION MARKET order {} at recorded price",
                    tx.exchangeOrderId());
            ObjectNode syntheticPayload = objectMapper.createObjectNode();
            syntheticPayload.put("order_id", tx.exchangeOrderId());
            syntheticPayload.put("status", "FILLED");
            syntheticPayload.put("filled_quantity", tx.quantity() != null ? tx.quantity() : 0);
            syntheticPayload.put("average_fill_price",
                    tx.price() != null ? tx.price().toPlainString() : "0");
            orderUpdateProcessor.process(syntheticPayload);
            return;
        }

        if (exchangeStatus.isBlank() || exchangeStatus.equals("PENDING")) return;

        log.info("Pending order sync: order {} has exchange status {} — triggering settlement",
                tx.exchangeOrderId(), exchangeStatus);

        // Build a payload compatible with OrderUpdateProcessor.process()
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("order_id", tx.exchangeOrderId());
        payload.put("status", exchangeStatus);

        // filled_quantity is returned as a string by the exchange (e.g. "10")
        String filledQtyStr = orderNode.path("filled_quantity").asText("0");
        try {
            payload.put("filled_quantity", (int) Double.parseDouble(filledQtyStr));
        } catch (NumberFormatException e) {
            payload.put("filled_quantity", tx.quantity() != null ? tx.quantity() : 0);
        }

        // average_fill_price is also a string (e.g. "100.50"); asText() on a TextNode returns it as-is
        String avgPriceStr = orderNode.path("average_fill_price").asText(
                tx.price() != null ? tx.price().toPlainString() : "0");
        payload.put("average_fill_price", avgPriceStr);

        orderUpdateProcessor.process(payload);
    }
}