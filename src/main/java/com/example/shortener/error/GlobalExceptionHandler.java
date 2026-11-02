package com.example.shortener.error;

import com.example.shortener.exception.InvalidUrlException;
import com.example.shortener.exception.KeyspaceExhaustedException;
import com.example.shortener.exception.ShortUrlNotFoundException;
import com.example.shortener.metrics.ShortenerMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Single source of truth for error → HTTP status mapping (RFC 9457
 * {@code ProblemDetail}). Domain exceptions stay HTTP-agnostic; only this
 * class decides status codes.
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private final ShortenerMetrics metrics;
    private final String notFoundHtml = readNotFoundHtml();

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        // getAllErrors() (not just getFieldErrors()) so a class-level constraint
        // violation (an ObjectError, not a FieldError) isn't silently dropped.
        List<Map<String, String>> errors = ex.getBindingResult().getAllErrors().stream()
                .map(err -> Map.of(
                        "field", err instanceof FieldError fieldError ? fieldError.getField() : "",
                        "message", String.valueOf(err.getDefaultMessage())))
                .toList();
        log.info("Validation failed: {}", errors);
        ProblemDetail problem = problemDetail(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Validation failed", request);
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        log.info("Malformed request body");
        return problemResponse(HttpStatus.BAD_REQUEST, ErrorCode.MALFORMED_REQUEST, "The request body is malformed", request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        log.info("Method not allowed: {}", ex.getMethod());
        return problemResponse(HttpStatus.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED,
                "This HTTP method is not allowed for this endpoint", request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        log.info("Unsupported media type: {}", ex.getContentType());
        return problemResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                "This content type is not supported", request);
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(
            NoResourceFoundException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        log.debug("No resource found: {}", ex.getResourcePath());
        return problemResponse(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "Resource not found", request);
    }

    @ExceptionHandler(InvalidUrlException.class)
    ResponseEntity<Object> handleInvalidUrl(InvalidUrlException ex, WebRequest request) {
        log.info("Invalid URL: {}", ex.getMessage());
        return problemResponse(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_URL, ex.getMessage(), request);
    }

    @ExceptionHandler(ShortUrlNotFoundException.class)
    ResponseEntity<Object> handleShortUrlNotFound(ShortUrlNotFoundException ex, WebRequest request) {
        log.debug("Short URL not found: {}", ex.getMessage());
        if (prefersHtml(request)) {
            metrics.recordError(ErrorCode.SHORT_URL_NOT_FOUND);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.TEXT_HTML).body(notFoundHtml);
        }
        return problemResponse(HttpStatus.NOT_FOUND, ErrorCode.SHORT_URL_NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(KeyspaceExhaustedException.class)
    ResponseEntity<Object> handleKeyspaceExhausted(KeyspaceExhaustedException ex, WebRequest request) {
        log.error("Keyspace exhausted", ex);
        return problemResponse(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.KEYSPACE_EXHAUSTED, ex.getMessage(), request);
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<Object> handleDataAccess(DataAccessException ex, WebRequest request) {
        log.error("Storage unavailable", ex);
        return problemResponse(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.STORAGE_UNAVAILABLE,
                "The storage backend is temporarily unavailable", request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unexpected error", ex);
        return problemResponse(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "An unexpected error occurred", request);
    }

    private ResponseEntity<Object> problemResponse(HttpStatus status, ErrorCode code, String detail, WebRequest request) {
        return ResponseEntity.status(status).body(problemDetail(status, code, detail, request));
    }

    private ProblemDetail problemDetail(HttpStatus status, ErrorCode code, String detail, WebRequest request) {
        metrics.recordError(code);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("urn:problem:" + code.toKebabCase()));
        problem.setTitle(status.getReasonPhrase());
        problem.setInstance(URI.create(path(request)));
        problem.setProperty("errorCode", code.name());
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    private static String path(WebRequest request) {
        if (request instanceof ServletWebRequest servletWebRequest) {
            return servletWebRequest.getRequest().getRequestURI();
        }
        return "";
    }

    /** True only when the client's top-ranked media type is exactly {@code text/html}, not a wildcard. */
    private static boolean prefersHtml(WebRequest request) {
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        if (accept == null || accept.isBlank()) {
            return false;
        }
        List<MediaType> accepted = MediaType.parseMediaTypes(accept);
        accepted.sort(Comparator.comparingDouble(MediaType::getQualityValue).reversed());
        MediaType top = accepted.get(0);
        return "text".equals(top.getType()) && "html".equals(top.getSubtype());
    }

    private static String readNotFoundHtml() {
        try {
            return new ClassPathResource("static/404.html").getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load static/404.html", e);
        }
    }
}
