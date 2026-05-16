package lynx.team2.controller;

import lombok.RequiredArgsConstructor;
import lynx.team2.client.ExchangeClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/options")
@RequiredArgsConstructor
public class OptionsController {

    private final ExchangeClient exchangeClient;

    /** Returns all active option contracts from the exchange. */
    @GetMapping
    public List<ExchangeClient.OptionSnapshot> getOptions() {
        return exchangeClient.getOptions().stream()
                .filter(ExchangeClient.OptionSnapshot::active)
                .toList();
    }
}
