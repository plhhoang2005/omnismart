package vn.omnismart.catalog;

import org.springframework.http.HttpStatus;

public class ProductCatalogException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ProductCatalogException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }

    static ProductCatalogException notFound() {
        return new ProductCatalogException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Product not found");
    }

    static ProductCatalogException archived() {
        return new ProductCatalogException(
                HttpStatus.CONFLICT, "PRODUCT_ARCHIVED", "Archived products cannot be changed");
    }

    static ProductCatalogException versionConflict() {
        return new ProductCatalogException(
                HttpStatus.CONFLICT,
                "PRODUCT_VERSION_CONFLICT",
                "The product was changed by another request; reload before retrying");
    }

    static ProductCatalogException skuConflict() {
        return new ProductCatalogException(
                HttpStatus.CONFLICT,
                "PRODUCT_SKU_CONFLICT",
                "SKU already exists in this store");
    }
}
