package com.einar115.thearchivist.exception.handler;

import com.einar115.thearchivist.dto.response.ErrorResponse;
import com.einar115.thearchivist.exception.DocumentNotFoundException;
import com.einar115.thearchivist.exception.DuplicateDocumentException;
import com.einar115.thearchivist.exception.InvalidDocumentFileException;
import com.einar115.thearchivist.exception.InvalidDocumentStateException;
import com.einar115.thearchivist.exception.UnreadableDocumentException;
import com.einar115.thearchivist.exception.UnsupportedDocumentTypeException;
import com.einar115.thearchivist.exception.UsernameAlreadyExistsException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

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

    @ExceptionHandler(DuplicateDocumentException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateDocumentException(DuplicateDocumentException ex, HttpServletRequest request) {
        LOGGER.warn(ex.getMessage());
        return buildErrorResponse(ex, HttpStatus.CONFLICT, request);
    }

    @ExceptionHandler(InvalidDocumentFileException.class)
    public ResponseEntity<ErrorResponse> handleInvalidDocumentFileException(InvalidDocumentFileException ex, HttpServletRequest request) {
        LOGGER.warn(ex.getMessage());
        return buildErrorResponse(ex, HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(UnsupportedDocumentTypeException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedDocumentTypeException(UnsupportedDocumentTypeException ex, HttpServletRequest request) {
        LOGGER.warn(ex.getMessage());
        return buildErrorResponse(ex, HttpStatus.UNSUPPORTED_MEDIA_TYPE, request);
    }

    @ExceptionHandler(UnreadableDocumentException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableDocumentException(UnreadableDocumentException ex, HttpServletRequest request) {
        // The cause, when there is one, is a parser failure worth keeping in the log.
        LOGGER.warn(ex.getMessage(), ex.getCause());
        return buildErrorResponse(ex, HttpStatus.UNPROCESSABLE_CONTENT, request);
    }

    @ExceptionHandler(DocumentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleDocumentNotFoundException(DocumentNotFoundException ex, HttpServletRequest request) {
        LOGGER.warn(ex.getMessage());
        return buildErrorResponse(ex, HttpStatus.NOT_FOUND, request);
    }

    @ExceptionHandler(InvalidDocumentStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidDocumentStateException(InvalidDocumentStateException ex, HttpServletRequest request) {
        LOGGER.warn(ex.getMessage());
        return buildErrorResponse(ex, HttpStatus.CONFLICT, request);
    }

    // A path variable or request parameter that cannot be converted, such as a malformed UUID.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatchException(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        LOGGER.warn("Invalid value for '{}' on {}", ex.getName(), request.getRequestURI());
        // Fixed message on purpose: the original one names the target Java type.
        return buildErrorResponse("Invalid value for '" + ex.getName() + "'", HttpStatus.BAD_REQUEST, request);
    }

    // A missing or malformed JSON body, such as a reject request sent without one.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadableException(HttpMessageNotReadableException ex, HttpServletRequest request) {
        LOGGER.warn("Unreadable request body on {}: {}", request.getRequestURI(), ex.getMessage());
        // Fixed message on purpose: the original one quotes the parser internals and the target type.
        return buildErrorResponse("Request body is missing or malformed", HttpStatus.BAD_REQUEST, request);
    }

    // Raised while the multipart request is parsed, before any controller runs.
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        LOGGER.warn("Upload too large on {}: {}", request.getRequestURI(), ex.getMessage());
        return buildErrorResponse("File exceeds the maximum upload size", HttpStatus.CONTENT_TOO_LARGE, request);
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
