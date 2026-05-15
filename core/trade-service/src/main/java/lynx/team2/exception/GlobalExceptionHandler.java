package lynx.team2.exception;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lynx.team2.exceptions.RepoException;
import lynx.team2.exceptions.ValidatorException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @ExceptionHandler(RepoException.class)
    public ResponseEntity<Map<String, String>> handleRepoException(RepoException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(ValidatorException.class)
    public ResponseEntity<Map<String, String>> handleValidatorException(ValidatorException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<Map<String, String>> handleRestClientException(RestClientResponseException e) {
        log.error("Downstream service error: status={} body={}", e.getStatusCode(), e.getResponseBodyAsString());
        return ResponseEntity.status(e.getStatusCode())
                .body(Map.of("error", extractMessage(e.getResponseBodyAsString())));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleException(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "An unexpected error occurred"));
    }

    private static String extractMessage(String body) {
        if (body == null || body.isBlank()) return "Service call failed";
        try {
            JsonNode root = MAPPER.readTree(body);
            // {"error": "..."} — our internal services
            if (root.path("error").isTextual()) return root.path("error").asText();
            // {"message": "..."} — Spring default error format
            if (root.path("message").isTextual()) return root.path("message").asText();
            // [{"msg": "..."}] or {"detail": [{"msg": "..."}]} — FastAPI/Pydantic
            JsonNode detail = root.path("detail");
            if (detail.isArray() && detail.size() > 0) {
                JsonNode first = detail.get(0);
                if (first.path("msg").isTextual()) return first.path("msg").asText();
            }
        } catch (Exception ignored) {}
        return "Service call failed";
    }
}
