package vn.omnismart.catalog;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.omnismart.audit.AuditAction;
import vn.omnismart.audit.AuditLogService;
import vn.omnismart.store.StoreAuthorizationService;
import vn.omnismart.store.StoreMember;
import vn.omnismart.store.StoreOperationGuard;

@Service
public class ProductService {

    private static final Pattern VALID_SKU = Pattern.compile("[A-Z0-9][A-Z0-9._-]{0,63}");

    private final ProductRepository productRepository;
    private final StoreAuthorizationService authorizationService;
    private final StoreOperationGuard storeOperationGuard;
    private final AuditLogService auditLogService;
    private final ProductPublicationGuard publicationGuard;

    public ProductService(
            ProductRepository productRepository,
            StoreAuthorizationService authorizationService,
            StoreOperationGuard storeOperationGuard,
            AuditLogService auditLogService,
            ProductPublicationGuard publicationGuard) {
        this.productRepository = productRepository;
        this.authorizationService = authorizationService;
        this.storeOperationGuard = storeOperationGuard;
        this.auditLogService = auditLogService;
        this.publicationGuard = publicationGuard;
    }

    @Transactional
    public ProductResponse create(
            OidcUser principal,
            UUID storeId,
            String rawSku,
            String rawName,
            String rawDescription,
            BigDecimal price,
            ProductCurrency currency,
            int inventoryQuantity) {
        StoreMember actor = authorizationService.requireMembership(principal, storeId);
        requireActiveStore(storeId);
        String sku = normalizeSku(rawSku);
        String name = normalizeName(rawName);
        if (productRepository.existsByStoreIdAndSku(storeId, sku)) {
            throw ProductCatalogException.skuConflict();
        }
        Product product = new Product(
                UUID.randomUUID(),
                storeId,
                sku,
                name,
                normalizeDescription(rawDescription),
                price,
                currency,
                inventoryQuantity);
        try {
            productRepository.saveAndFlush(product);
        } catch (org.springframework.dao.DataIntegrityViolationException exception) {
            throw ProductCatalogException.skuConflict();
        }
        auditLogService.record(
                storeId,
                actor.getUserId(),
                AuditAction.PRODUCT_CREATED,
                "PRODUCT",
                product.getId(),
                "sku=" + sku);
        return response(product);
    }

    @Transactional(readOnly = true)
    public ProductPageResponse list(
            OidcUser principal,
            UUID storeId,
            int page,
            int size,
            String rawSearch,
            ProductStatus status) {
        authorizationService.requireMembership(principal, storeId);
        String search = likeSearch(rawSearch);
        List<ProductStatus> statuses = status == null
                ? List.of(ProductStatus.ACTIVE, ProductStatus.ARCHIVED)
                : List.of(status);
        Page<Product> products = productRepository.search(
                storeId,
                statuses,
                search,
                PageRequest.of(
                        page,
                        size,
                        Sort.by(Sort.Direction.DESC, "createdAt")
                                .and(Sort.by(Sort.Direction.DESC, "id"))));
        return new ProductPageResponse(
                products.getContent().stream().map(this::response).toList(),
                products.getNumber(),
                products.getSize(),
                products.getTotalElements(),
                products.getTotalPages());
    }

    @Transactional(readOnly = true)
    public ProductResponse get(OidcUser principal, UUID storeId, UUID productId) {
        authorizationService.requireMembership(principal, storeId);
        return response(requireProduct(storeId, productId));
    }

    @Transactional
    public ProductResponse update(
            OidcUser principal,
            UUID storeId,
            UUID productId,
            long expectedVersion,
            String rawSku,
            String rawName,
            String rawDescription,
            BigDecimal price,
            ProductCurrency currency,
            Integer inventoryQuantity) {
        StoreMember actor = authorizationService.requireMembership(principal, storeId);
        requireActiveStore(storeId);
        Product product = requireProduct(storeId, productId);
        requireEditableVersion(product, expectedVersion);
        if (product.getStatus() == ProductStatus.ARCHIVED) {
            throw ProductCatalogException.archived();
        }

        String sku = rawSku == null ? null : normalizeSku(rawSku);
        String name = rawName == null ? null : normalizeName(rawName);
        if (sku != null
                && !sku.equals(product.getSku())
                && productRepository.existsByStoreIdAndSku(storeId, sku)) {
            throw ProductCatalogException.skuConflict();
        }
        List<String> changedFields = changedFields(
                rawSku, rawName, rawDescription, price, currency, inventoryQuantity);
        if (changedFields.isEmpty()) {
            throw new ProductCatalogException(
                    HttpStatus.BAD_REQUEST,
                    "PRODUCT_UPDATE_EMPTY",
                    "At least one product field must be supplied");
        }
        product.update(
                sku,
                name,
                rawDescription == null ? null : normalizeDescription(rawDescription),
                rawDescription != null,
                price,
                currency,
                inventoryQuantity);
        flushWithOptimisticConflict(product);
        auditLogService.record(
                storeId,
                actor.getUserId(),
                AuditAction.PRODUCT_UPDATED,
                "PRODUCT",
                productId,
                "fields=" + String.join(",", changedFields));
        return response(product);
    }

    @Transactional
    public void archive(
            OidcUser principal,
            UUID storeId,
            UUID productId,
            long expectedVersion,
            String confirmationSku) {
        StoreMember actor = authorizationService.requireMembership(principal, storeId);
        requireActiveStore(storeId);
        Product product = requireProduct(storeId, productId);
        requireEditableVersion(product, expectedVersion);
        if (!product.getSku().equals(normalizeSku(confirmationSku))) {
            throw new ProductCatalogException(
                    HttpStatus.BAD_REQUEST,
                    "PRODUCT_ARCHIVE_CONFIRMATION_MISMATCH",
                    "SKU confirmation does not match");
        }
        if (publicationGuard.hasActivePublishingJobs(storeId, productId)) {
            throw new ProductCatalogException(
                    HttpStatus.CONFLICT,
                    "PRODUCT_HAS_ACTIVE_PUBLISHING_JOBS",
                    "Cancel active publishing jobs before archiving this product");
        }
        if (product.getStatus() == ProductStatus.ARCHIVED) {
            return;
        }
        product.archive();
        flushWithOptimisticConflict(product);
        auditLogService.record(
                storeId,
                actor.getUserId(),
                AuditAction.PRODUCT_ARCHIVED,
                "PRODUCT",
                productId,
                "sku=" + product.getSku());
    }

    Product requireActiveProduct(UUID storeId, UUID productId) {
        Product product = requireProductForStore(storeId, productId);
        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw ProductCatalogException.archived();
        }
        return product;
    }

    Product requireProductForStore(UUID storeId, UUID productId) {
        return requireProduct(storeId, productId);
    }

    void requireActiveStore(UUID storeId) {
        storeOperationGuard.requireOperational(storeId);
    }

    private Product requireProduct(UUID storeId, UUID productId) {
        return productRepository.findByIdAndStoreId(productId, storeId)
                .orElseThrow(ProductCatalogException::notFound);
    }

    private void requireEditableVersion(Product product, long expectedVersion) {
        if (product.getVersion() != expectedVersion) {
            throw ProductCatalogException.versionConflict();
        }
    }

    private void flushWithOptimisticConflict(Product product) {
        try {
            productRepository.saveAndFlush(product);
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw ProductCatalogException.versionConflict();
        } catch (org.springframework.dao.DataIntegrityViolationException exception) {
            throw ProductCatalogException.skuConflict();
        }
    }

    private String normalizeSku(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!VALID_SKU.matcher(normalized).matches()) {
            throw new ProductCatalogException(
                    HttpStatus.BAD_REQUEST,
                    "PRODUCT_SKU_INVALID",
                    "SKU must use 1-64 letters, numbers, dots, underscores or hyphens");
        }
        return normalized;
    }

    private String normalizeName(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new ProductCatalogException(
                    HttpStatus.BAD_REQUEST, "PRODUCT_NAME_INVALID", "Product name is required");
        }
        return normalized;
    }

    private String normalizeDescription(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String likeSearch(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String escaped = value.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private List<String> changedFields(
            String sku,
            String name,
            String description,
            BigDecimal price,
            ProductCurrency currency,
            Integer inventoryQuantity) {
        List<String> fields = new ArrayList<>();
        if (sku != null) fields.add("sku");
        if (name != null) fields.add("name");
        if (description != null) fields.add("description");
        if (price != null) fields.add("price");
        if (currency != null) fields.add("currency");
        if (inventoryQuantity != null) fields.add("inventoryQuantity");
        return fields;
    }

    private ProductResponse response(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getStoreId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getCurrency(),
                product.getInventoryQuantity(),
                product.getStatus(),
                product.getVersion(),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }

    public record ProductResponse(
            UUID id,
            UUID storeId,
            String sku,
            String name,
            String description,
            BigDecimal price,
            ProductCurrency currency,
            int inventoryQuantity,
            ProductStatus status,
            long version,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {

        @Override
        public String toString() {
            return "ProductResponse[id=" + id + ", storeId=" + storeId
                    + ", sku=" + sku + ", name=" + name
                    + ", price=[REDACTED], inventoryQuantity=[REDACTED]"
                    + ", currency=" + currency + ", status=" + status
                    + ", version=" + version + "]";
        }
    }

    public record ProductPageResponse(
            List<ProductResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }
}
