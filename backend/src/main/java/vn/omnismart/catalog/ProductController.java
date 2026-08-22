package vn.omnismart.catalog;

import java.math.BigDecimal;
import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import vn.omnismart.catalog.ProductService.ProductPageResponse;
import vn.omnismart.catalog.ProductService.ProductResponse;

@Validated
@RestController
@RequestMapping("/api/v1/stores/{storeId}/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    ResponseEntity<ProductResponse> create(
            @AuthenticationPrincipal OidcUser principal,
            @PathVariable UUID storeId,
            @Valid @RequestBody CreateProductRequest request) {
        ProductResponse created = productService.create(
                principal,
                storeId,
                request.sku(),
                request.name(),
                request.description(),
                request.price(),
                request.currency(),
                request.inventoryQuantity());
        return ResponseEntity.created(
                        URI.create("/api/v1/stores/" + storeId + "/products/" + created.id()))
                .body(created);
    }

    @GetMapping
    ProductPageResponse list(
            @AuthenticationPrincipal OidcUser principal,
            @PathVariable UUID storeId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "") @Size(max = 160) String search,
            @RequestParam(required = false) ProductStatus status) {
        return productService.list(principal, storeId, page, size, search, status);
    }

    @GetMapping("/{productId}")
    ProductResponse get(
            @AuthenticationPrincipal OidcUser principal,
            @PathVariable UUID storeId,
            @PathVariable UUID productId) {
        return productService.get(principal, storeId, productId);
    }

    @PatchMapping("/{productId}")
    ProductResponse update(
            @AuthenticationPrincipal OidcUser principal,
            @PathVariable UUID storeId,
            @PathVariable UUID productId,
            @Valid @RequestBody UpdateProductRequest request) {
        return productService.update(
                principal,
                storeId,
                productId,
                request.version(),
                request.sku(),
                request.name(),
                request.description(),
                request.price(),
                request.currency(),
                request.inventoryQuantity());
    }

    @DeleteMapping("/{productId}")
    ResponseEntity<Void> archive(
            @AuthenticationPrincipal OidcUser principal,
            @PathVariable UUID storeId,
            @PathVariable UUID productId,
            @Valid @RequestBody ArchiveProductRequest request) {
        productService.archive(
                principal, storeId, productId, request.version(), request.confirmationSku());
        return ResponseEntity.noContent().build();
    }

    record CreateProductRequest(
            @NotBlank @Size(max = 64) String sku,
            @NotBlank @Size(max = 160) String name,
            @Size(max = 5000) String description,
            @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal price,
            @NotNull ProductCurrency currency,
            @Min(0) int inventoryQuantity) {

        @Override
        public String toString() {
            return "CreateProductRequest[sku=" + sku + ", name=" + name
                    + ", price=[REDACTED], currency=" + currency
                    + ", inventoryQuantity=[REDACTED]]";
        }
    }

    record UpdateProductRequest(
            @NotNull @PositiveOrZero Long version,
            @Size(max = 64) String sku,
            @Size(max = 160) String name,
            @Size(max = 5000) String description,
            @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal price,
            ProductCurrency currency,
            @Min(0) Integer inventoryQuantity) {

        @Override
        public String toString() {
            return "UpdateProductRequest[version=" + version + ", sku=" + sku
                    + ", name=" + name + ", price=[REDACTED], currency=" + currency
                    + ", inventoryQuantity=[REDACTED]]";
        }
    }

    record ArchiveProductRequest(
            @NotNull @PositiveOrZero Long version,
            @NotBlank @Size(max = 64) String confirmationSku) {
    }
}
