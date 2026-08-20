package vn.omnismart.common.api;

import java.time.OffsetDateTime;
import java.util.List;

public record ApiErrorResponse(
        String code,
        String message,
        List<FieldError> fieldErrors,
        String traceId,
        String path,
        OffsetDateTime timestamp) {

    public record FieldError(String field, String message) {
    }
}
