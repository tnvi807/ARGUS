package com.argus.common;

import org.springframework.http.HttpStatus;

/** Controlled API error that is safe to render to clients (never contains secrets). */
public class ApiErrorException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiErrorException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public ApiErrorException(HttpStatus status, String message) {
        this(status, status.name().toLowerCase(), message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public static ApiErrorException notFound(String what) {
        return new ApiErrorException(HttpStatus.NOT_FOUND, "not_found", what + " not found");
    }

    public static ApiErrorException badRequest(String message) {
        return new ApiErrorException(HttpStatus.BAD_REQUEST, "bad_request", message);
    }

    public static ApiErrorException forbidden(String message) {
        return new ApiErrorException(HttpStatus.FORBIDDEN, "forbidden", message);
    }

    public static ApiErrorException unauthorized(String message) {
        return new ApiErrorException(HttpStatus.UNAUTHORIZED, "unauthenticated", message);
    }
}
