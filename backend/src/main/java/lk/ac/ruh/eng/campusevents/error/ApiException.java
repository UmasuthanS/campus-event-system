package lk.ac.ruh.eng.campusevents.error;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final java.util.Map<String, String> errors;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, java.util.Map.of());
    }

    public ApiException(HttpStatus status, String code, String message, java.util.Map<String, String> errors) {
        super(message);
        this.status = status;
        this.code = code;
        this.errors = errors;
    }

    public HttpStatus getStatus() { return status; }
    public java.util.Map<String, String> getErrors() { return errors; }
    public String getCode() { return code; }
}
