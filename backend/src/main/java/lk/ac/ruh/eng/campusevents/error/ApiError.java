package lk.ac.ruh.eng.campusevents.error;

import java.util.Map;

public record ApiError(int status, String code, String message, Map<String, String> errors) {
    public ApiError(int status, String code, String message) {
        this(status, code, message, Map.of());
    }
}
