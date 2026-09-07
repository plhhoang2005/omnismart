package vn.omnismart.content;

import org.springframework.http.HttpStatus;

import vn.omnismart.common.api.ApiException;

public class ContentException extends ApiException {

    private ContentException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }

    static ContentException notFound() {
        return new ContentException(HttpStatus.NOT_FOUND, "CONTENT_NOT_FOUND", "Content not found");
    }

    static ContentException productNotFound() {
        return new ContentException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Product not found");
    }

    static ContentException productInactive() {
        return new ContentException(
                HttpStatus.CONFLICT,
                "CONTENT_PRODUCT_INACTIVE",
                "Content can only be changed or approved for an active product");
    }

    static ContentException bodyInvalid() {
        return new ContentException(
                HttpStatus.BAD_REQUEST,
                "CONTENT_BODY_INVALID",
                "Content body must contain between 1 and 10000 characters");
    }

    static ContentException unchanged() {
        return new ContentException(
                HttpStatus.BAD_REQUEST,
                "CONTENT_UPDATE_UNCHANGED",
                "Content body must be changed before a new version is created");
    }

    static ContentException rejectionReasonInvalid() {
        return new ContentException(
                HttpStatus.BAD_REQUEST,
                "CONTENT_REJECTION_REASON_INVALID",
                "A rejection reason between 1 and 1000 characters is required");
    }

    static ContentException versionConflict() {
        return new ContentException(
                HttpStatus.CONFLICT,
                "CONTENT_VERSION_CONFLICT",
                "The content was changed by another request; reload before retrying");
    }

    static ContentException invalidTransition(String message) {
        return new ContentException(HttpStatus.CONFLICT, "CONTENT_INVALID_TRANSITION", message);
    }

    static ContentException approvalNotFound() {
        return new ContentException(
                HttpStatus.CONFLICT,
                "CONTENT_APPROVAL_NOT_PENDING",
                "No pending approval exists for the current content version");
    }
}
