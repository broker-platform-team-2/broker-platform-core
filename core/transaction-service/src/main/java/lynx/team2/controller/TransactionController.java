package lynx.team2.controller;

import lynx.team2.dto.CreateTransactionRequest;
import lynx.team2.dto.TransactionResponse;
import lynx.team2.exceptions.RepoException;
import lynx.team2.models.Transaction;
import lynx.team2.models.TransactionStatus;
import lynx.team2.models.User;
import lynx.team2.repository.UserRepository;
import lynx.team2.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;
    private final UserRepository userRepository;

    @Value("${internal.token:}")
    private String internalToken;

    // --- User-facing endpoints (userId injected by gateway as X-User-Id header) ---

    @GetMapping
    public List<TransactionResponse> getTransactions(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime date
    ) {
        List<Transaction> transactions;
        if (date != null) {
            transactions = transactionService.findAllForUserIdAndDate(userId, date);
        } else if (from != null && to != null) {
            transactions = transactionService.findAllForUserIdAndDateRange(userId, from, to);
        } else {
            transactions = transactionService.findAllForUserId(userId);
        }
        return transactions.stream().map(this::toResponse).toList();
    }

    // --- Internal endpoints (called by other services; userId in request body) ---

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse createTransaction(@RequestBody CreateTransactionRequest request) {
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new RepoException("User not found: " + request.userId()));

        Transaction transaction = Transaction.builder()
                .user(user)
                .exchangeOrderId(request.exchangeOrderId())
                .type(request.type())
                .status(request.status())
                .platformFee(request.platformFee())
                .price(request.price())
                .currency(request.currency())
                .quantity(request.quantity())
                .instrumentId(request.instrumentId())
                .instrumentType(request.instrumentType())
                .build();

        return toResponse(transactionService.createTransaction(transaction));
    }

    // --- Settlement endpoints (called by exchange-client-service, internal token required) ---

    @GetMapping("/by-exchange-order/{exchangeOrderId}")
    public TransactionResponse getByExchangeOrderId(
            @RequestHeader("X-Internal-Token") String token,
            @PathVariable String exchangeOrderId) {
        verifyToken(token);
        Transaction t = transactionService.findByExchangeOrderId(exchangeOrderId)
                .orElseThrow(() -> new RepoException("Transaction not found for exchangeOrderId=" + exchangeOrderId));
        return toResponse(t);
    }

    @PatchMapping("/exchange-order/{exchangeOrderId}/status")
    public TransactionResponse updateStatus(
            @RequestHeader("X-Internal-Token") String token,
            @PathVariable String exchangeOrderId,
            @RequestParam TransactionStatus status) {
        verifyToken(token);
        return toResponse(transactionService.updateStatus(exchangeOrderId, status));
    }

    private void verifyToken(String token) {
        if (!internalToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTransaction(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id) {
        Transaction transaction = transactionService.findById(id)
                .orElseThrow(() -> new RepoException("Transaction not found: " + id));
        if (!transaction.getUser().getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
        transactionService.deleteTransaction(id);
    }

    private TransactionResponse toResponse(Transaction t) {
        return new TransactionResponse(
                t.getTransactionId(),
                t.getUser().getUserId(),
                t.getExchangeOrderId(),
                t.getType(),
                t.getStatus(),
                t.getPlatformFee(),
                t.getPrice(),
                t.getCurrency(),
                t.getQuantity(),
                t.getDate(),
                t.getInstrumentId(),
                t.getInstrumentType()
        );
    }
}
