package lk.ac.ruh.eng.campusevents.error;

import java.util.LinkedHashMap;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> api(ApiException exception) {
        return ResponseEntity.status(exception.getStatus()).body(new ApiError(
                exception.getStatus().value(), exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception) {
        var errors = new LinkedHashMap<String, String>();
        exception.getBindingResult().getFieldErrors().forEach(error -> errors.putIfAbsent(
                error.getField().equals("passwordWithinLimit") ? "password" : error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(new ApiError(400, "VALIDATION_ERROR", "Check the highlighted fields.", errors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> malformed() {
        return ResponseEntity.badRequest().body(new ApiError(400, "INVALID_REQUEST", "Request contains invalid or unsupported fields."));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    ResponseEntity<ApiError> duplicate() {
        return ResponseEntity.status(409).body(new ApiError(409, "DUPLICATE_USER", "Email or registration number is already registered."));
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ApiError> databaseUnavailable() {
        return ResponseEntity.status(503).body(new ApiError(503, "DATABASE_UNAVAILABLE", "Service temporarily unavailable. Please try again."));
    }
}
