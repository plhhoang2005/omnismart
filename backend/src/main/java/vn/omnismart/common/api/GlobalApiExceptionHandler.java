package vn.omnismart.common.api;

import java.time.OffsetDateTime;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.server.ResponseStatusException;

import vn.omnismart.catalog.ProductCatalogException;
import vn.omnismart.membership.InvitationExpiredException;

@RestControllerAdvice
public class GlobalApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiErrorResponse> handleApiException(
            ApiException exception,
            HttpServletRequest request) {
        return response(exception.getStatus(), exception.getCode(), exception.getMessage(), List.of(), request);
    }

    @ExceptionHandler(ProductCatalogException.class)
    ResponseEntity<ApiErrorResponse> handleCatalogException(
            ProductCatalogException exception,
            HttpServletRequest request) {
        return response(exception.getStatus(), exception.getCode(), exception.getMessage(), List.of(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        List<ApiErrorResponse.FieldError> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiErrorResponse.FieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        return response(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                "Request validation failed",
                fieldErrors,
                request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResponse> handleUnreadableMessage(
            HttpMessageNotReadableException exception,
            HttpServletRequest request) {
        return response(
                HttpStatus.BAD_REQUEST,
                "REQUEST_BODY_INVALID",
                "Request body is missing or malformed",
                List.of(),
                request);
    }

    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            HandlerMethodValidationException.class
    })
    ResponseEntity<ApiErrorResponse> handleInvalidRequestParameter(
            Exception exception,
            HttpServletRequest request) {
        return response(
                HttpStatus.BAD_REQUEST,
                "REQUEST_PARAMETER_INVALID",
                "A path or query parameter is invalid",
                List.of(),
                request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiErrorResponse> handleMultipartLimit(
            MaxUploadSizeExceededException exception,
            HttpServletRequest request) {
        return response(
                HttpStatus.CONTENT_TOO_LARGE,
                "PRODUCT_MEDIA_TOO_LARGE",
                "Image exceeds the configured maximum size",
                List.of(),
                request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiErrorResponse> handleResponseStatus(
            ResponseStatusException exception,
            HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        String message = exception.getReason() == null ? status.getReasonPhrase() : exception.getReason();
        return response(status, status.name(), message, List.of(), request);
    }

    @ExceptionHandler(InvitationExpiredException.class)
    ResponseEntity<ApiErrorResponse> handleExpiredInvitation(
            InvitationExpiredException exception,
            HttpServletRequest request) {
        return response(
                HttpStatus.GONE,
                "INVITATION_EXPIRED",
                exception.getMessage(),
                List.of(),
                request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request) {
        return response(
                HttpStatus.METHOD_NOT_ALLOWED,
                "METHOD_NOT_ALLOWED",
                "HTTP method is not supported for this resource",
                List.of(),
                request);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ApiErrorResponse> handleOptimisticConflict(
            ObjectOptimisticLockingFailureException exception,
            HttpServletRequest request) {
        return response(
                HttpStatus.CONFLICT,
                "PRODUCT_VERSION_CONFLICT",
                "The product was changed by another request; reload before retrying",
                List.of(),
                request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleUnexpected(
            Exception exception,
            HttpServletRequest request) {
        LOGGER.error("Unhandled API exception", exception);
        return response(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "An unexpected error occurred",
                List.of(),
                request);
    }

    private ResponseEntity<ApiErrorResponse> response(
            HttpStatus status,
            String code,
            String message,
            List<ApiErrorResponse.FieldError> fieldErrors,
            HttpServletRequest request) {
        Object requestId = request.getAttribute(RequestCorrelationFilter.ATTRIBUTE_NAME);
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                code,
                message,
                fieldErrors,
                requestId == null ? null : requestId.toString(),
                request.getRequestURI(),
                OffsetDateTime.now()));
    }
}
