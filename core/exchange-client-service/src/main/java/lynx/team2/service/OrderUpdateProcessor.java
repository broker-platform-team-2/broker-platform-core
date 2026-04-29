package lynx.team2.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lynx.team2.client.NotificationServiceClient;
import lynx.team2.dto.NotificationMessage;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Processes ORDER_UPDATE messages from the exchange. The minimum responsibility
 * is to forward the update to notification-service so the end user sees a status
 * change. Full settlement (deduct frozen funds, create/update holdings) requires
 * mapping the exchange order_id back to our internal user + transaction; that's
 * a TODO once the exchange order_id <-> our transaction mapping is finalized.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderUpdateProcessor {

    private final ObjectMapper objectMapper;
    private final NotificationServiceClient notificationClient;

    public void process(JsonNode payload) {
        String orderId = payload.path("order_id").asText();
        String status = payload.path("status").asText();
        log.info("ORDER_UPDATE received: order_id={} status={}", orderId, status);

        notificationClient.broadcast(new NotificationMessage(
                "ORDER_UPDATE",
                objectMapper.convertValue(payload, Map.class)
        ));

        // TODO: on FILLED / PARTIALLY_FILLED:
        //   1. Look up our Transaction by exchangeOrderId
        //   2. Update transaction.status, filled_quantity, exchange_fee
        //   3. Call accountService.deductFrozenFunds for the cash settled
        //   4. Call holdingsService to upsert the position with new average_cost
        // Requires: TransactionService.findByExchangeOrderId endpoint to be exposed
    }
}
