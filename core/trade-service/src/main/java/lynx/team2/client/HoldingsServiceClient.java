package lynx.team2.client;

import lynx.team2.dto.HoldingResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Arrays;
import java.util.List;

@Component("holdingsClientService")
public class HoldingsServiceClient {

    private final RestClient client;

    public HoldingsServiceClient(@Qualifier("holdingsServiceClient") RestClient client) {
        this.client = client;
    }

    public List<HoldingResponse> getHoldingsForInstrument(Long userId, String instrumentId) {
        HoldingResponse[] arr = client.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/holdings")
                        .queryParam("instrumentId", instrumentId)
                        .build())
                .header("X-User-Id", String.valueOf(userId))
                .retrieve()
                .body(HoldingResponse[].class);
        return arr != null ? Arrays.asList(arr) : List.of();
    }
}