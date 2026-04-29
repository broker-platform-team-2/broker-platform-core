package lynx.team2.controller;

import lynx.team2.dto.CreateHoldingRequest;
import lynx.team2.dto.HoldingResponse;
import lynx.team2.dto.UpdateHoldingRequest;
import lynx.team2.exceptions.RepoException;
import lynx.team2.models.Holding;
import lynx.team2.models.User;
import lynx.team2.repository.HoldingsRepository;
import lynx.team2.repository.UserRepository;
import lynx.team2.service.HoldingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class HoldingsController {

    private final HoldingsService holdingsService;
    private final HoldingsRepository holdingsRepository;
    private final UserRepository userRepository;

    // --- User-facing endpoints (userId injected by gateway as X-User-Id header) ---

    @GetMapping("/holdings")
    public List<HoldingResponse> getHoldings(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(required = false) String instrumentId
    ) {
        List<Holding> holdings = instrumentId != null
                ? holdingsService.findAllForUserIdAndInstrumentId(userId, instrumentId)
                : holdingsService.findAllForUserId(userId);
        return holdings.stream().map(this::toResponse).toList();
    }

    @GetMapping("/portfolio")
    public List<HoldingResponse> getPortfolio(@RequestHeader("X-User-Id") Long userId) {
        return holdingsService.findAllForUserId(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    // --- Internal endpoints (called by other services; userId in request body) ---

    @PostMapping("/holdings")
    @ResponseStatus(HttpStatus.CREATED)
    public HoldingResponse createHolding(@RequestBody CreateHoldingRequest request) {
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new RepoException("User not found: " + request.userId()));

        Holding holding = Holding.builder()
                .user(user)
                .instrumentType(request.instrumentType())
                .instrumentId(request.instrumentId())
                .amount(request.amount())
                .currency(request.currency())
                .averageCost(request.averageCost())
                .build();

        return toResponse(holdingsService.createHolding(holding));
    }

    @PutMapping("/holdings/{id}")
    public HoldingResponse updateHolding(
            @PathVariable Long id,
            @RequestBody UpdateHoldingRequest request
    ) {
        Holding existing = holdingsRepository.findById(id)
                .orElseThrow(() -> new RepoException("Holding not found: " + id));

        existing.setAmount(request.amount());
        existing.setAverageCost(request.averageCost());

        return toResponse(holdingsService.updateHolding(existing));
    }

    @DeleteMapping("/holdings/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteHolding(@PathVariable Long id) {
        holdingsService.deleteHolding(id);
    }

    private HoldingResponse toResponse(Holding h) {
        return new HoldingResponse(
                h.getHoldingId(),
                h.getUser().getUserId(),
                h.getInstrumentType(),
                h.getInstrumentId(),
                h.getAmount(),
                h.getCurrency(),
                h.getAverageCost()
        );
    }
}
