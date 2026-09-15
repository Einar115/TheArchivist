package com.einar115.thearchivist.exception.handler;

import com.einar115.thearchivist.dto.response.ErrorResponse;
import com.einar115.thearchivist.exception.UsernameAlreadyExistsException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(UsernameAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleUsernameAlreadyExistsException(UsernameAlreadyExistsException ex, HttpServletRequest request) {
        LOGGER.warn(ex.getMessage());
        return buildErrorResponse(ex, HttpStatus.CONFLICT, request);
    }

    /**
     * Raised by AuthenticationManager#authenticate inside the login endpoint. Unauthenticated access to a
     * protected resource never reaches this advice: that is translated by the AuthenticationEntryPoint,
     * because it originates in the filter chain, before the DispatcherServlet.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException ex, HttpServletRequest request) {
        LOGGER.warn("Failed login attempt on {}: {}", request.getRequestURI(), ex.getMessage());
        // Fixed message on purpose: DisabledException and LockedException would reveal that the account exists.
        return buildErrorResponse("Invalid username or password", HttpStatus.UNAUTHORIZED, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        // A single field can break several constraints at once (password is @NotBlank and @Size),
        // so a merge function is required or Collectors.toMap throws on the duplicate key.
        Map<String, String> validationErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        error -> error.getDefaultMessage() == null ? "invalid" : error.getDefaultMessage(),
                        (first, second) -> first));

        LOGGER.warn("Invalid request on {}: {}", request.getRequestURI(), validationErrors);
        return ResponseEntity.badRequest()
                .body(ErrorResponse.ofValidation(request.getRequestURI(), validationErrors));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception ex, HttpServletRequest request) {
        // Passing the exception last logs the full stack trace; the client only gets a generic message.
        LOGGER.error("Unexpected error on {}", request.getRequestURI(), ex);
        return buildErrorResponse("Unexpected error", HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    //Build default exceptions response
    private ResponseEntity<ErrorResponse> buildErrorResponse(Exception ex, HttpStatus status, HttpServletRequest request) {
        return buildErrorResponse(ex.getMessage(), status, request);
    }

    // Overload for statuses whose message must not expose the original exception.
    private ResponseEntity<ErrorResponse> buildErrorResponse(String message, HttpStatus status, HttpServletRequest request) {
        return ResponseEntity.status(status).body(
                ErrorResponse.of(status.value(),
                        status.getReasonPhrase(),
                        message,
                        request.getRequestURI()
                )
        );
    }

}
