package vn.omnismart.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import vn.omnismart.audit.AuditAction;
import vn.omnismart.audit.AuditLogService;
import vn.omnismart.catalog.ImageContentInspector.InspectedImage;
import vn.omnismart.catalog.storage.FileSizeLimitExceededException;
import vn.omnismart.catalog.storage.MediaStorage;
import vn.omnismart.store.StoreAuthorizationService;
import vn.omnismart.store.StoreMember;

@Service
public class ProductMediaService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProductMediaService.class);

    private final ProductRepository productRepository;
    private final ProductMediaRepository mediaRepository;
    private final ProductService productService;
    private final StoreAuthorizationService authorizationService;
    private final AuditLogService auditLogService;
    private final MediaStorage storage;
    private final ImageContentInspector imageInspector;
    private final long maximumFileBytes;
    private final int maximumMediaPerProduct;

    public ProductMediaService(
            ProductRepository productRepository,
            ProductMediaRepository mediaRepository,
            ProductService productService,
            StoreAuthorizationService authorizationService,
            AuditLogService auditLogService,
            MediaStorage storage,
            ImageContentInspector imageInspector,
            @Value("${omnismart.product-media.max-file-size:5MB}") DataSize maximumFileSize,
            @Value("${omnismart.product-media.max-images-per-product:8}") int maximumMediaPerProduct) {
        if (maximumFileSize.toBytes() <= 0) {
            throw new IllegalArgumentException("Product media maximum file size must be positive");
        }
        if (maximumMediaPerProduct < 1 || maximumMediaPerProduct > 20) {
            throw new IllegalArgumentException("Product media count must be between 1 and 20");
        }
        this.productRepository = productRepository;
        this.mediaRepository = mediaRepository;
        this.productService = productService;
        this.authorizationService = authorizationService;
        this.auditLogService = auditLogService;
        this.storage = storage;
        this.imageInspector = imageInspector;
        this.maximumFileBytes = maximumFileSize.toBytes();
        this.maximumMediaPerProduct = maximumMediaPerProduct;
    }

    @Transactional
    public MediaResponse upload(
            OidcUser principal,
            UUID storeId,
            UUID productId,
            MultipartFile file,
            boolean selectAsPrimary) {
        StoreMember actor = authorizationService.requireMembership(principal, storeId);
        productService.requireActiveStore(storeId);
        Product product = productRepository.findLockedByIdAndStoreId(productId, storeId)
                .orElseThrow(ProductCatalogException::notFound);
        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw ProductCatalogException.archived();
        }
        validateUploadEnvelope(file);
        long currentCount = mediaRepository.countByStoreIdAndProductIdAndStatus(
                storeId, productId, ProductMediaStatus.ATTACHED);
        if (currentCount >= maximumMediaPerProduct) {
            throw new ProductCatalogException(
                    HttpStatus.CONFLICT,
                    "PRODUCT_MEDIA_LIMIT_REACHED",
                    "A product can have at most " + maximumMediaPerProduct + " images");
        }

        UUID mediaId = UUID.randomUUID();
        String objectKey = storeId + "/" + productId + "/" + mediaId;
        ProductMedia media = new ProductMedia(mediaId, storeId, objectKey);
        mediaRepository.saveAndFlush(media);

        boolean promoted = false;
        try {
            long actualSize = storage.storeTemporary(mediaId, file.getInputStream(), maximumFileBytes);
            InspectedImage inspected;
            try (InputStream temporary = storage.openTemporary(mediaId)) {
                inspected = imageInspector.inspect(temporary);
            }
            storage.promote(mediaId, objectKey);
            promoted = true;
            if (selectAsPrimary) {
                mediaRepository.lockAttachedMedia(storeId, productId)
                        .forEach(ProductMedia::clearPrimary);
            }
            media.attach(productId, inspected.contentType(), actualSize, selectAsPrimary);
            mediaRepository.saveAndFlush(media);
            auditLogService.record(
                    storeId,
                    actor.getUserId(),
                    AuditAction.PRODUCT_MEDIA_ATTACHED,
                    "PRODUCT_MEDIA",
                    mediaId,
                    "productId=" + productId + ",primary=" + selectAsPrimary);
            return response(media);
        } catch (FileSizeLimitExceededException exception) {
            compensateStorage(mediaId, objectKey, promoted);
            throw tooLarge();
        } catch (ProductCatalogException exception) {
            compensateStorage(mediaId, objectKey, promoted);
            throw exception;
        } catch (IOException exception) {
            compensateStorage(mediaId, objectKey, promoted);
            throw new ProductCatalogException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "PRODUCT_MEDIA_STORAGE_FAILED",
                    "The image could not be stored");
        } catch (RuntimeException exception) {
            compensateStorage(mediaId, objectKey, promoted);
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public List<MediaResponse> list(
            OidcUser principal, UUID storeId, UUID productId) {
        authorizationService.requireMembership(principal, storeId);
        productService.requireProductForStore(storeId, productId);
        return mediaRepository.findByStoreIdAndProductIdAndStatusOrderByCreatedAtAsc(
                        storeId, productId, ProductMediaStatus.ATTACHED)
                .stream()
                .map(this::response)
                .toList();
    }

    @Transactional
    public MediaResponse selectPrimary(
            OidcUser principal, UUID storeId, UUID productId, UUID mediaId) {
        StoreMember actor = authorizationService.requireMembership(principal, storeId);
        productService.requireActiveStore(storeId);
        productRepository.findLockedByIdAndStoreId(productId, storeId)
                .filter(product -> product.getStatus() == ProductStatus.ACTIVE)
                .orElseThrow(ProductCatalogException::notFound);
        List<ProductMedia> media = mediaRepository.lockAttachedMedia(storeId, productId);
        ProductMedia selected = media.stream()
                .filter(candidate -> candidate.getId().equals(mediaId))
                .findFirst()
                .orElseThrow(() -> new ProductCatalogException(
                        HttpStatus.NOT_FOUND, "PRODUCT_MEDIA_NOT_FOUND", "Product image not found"));
        if (!selected.isPrimary()) {
            media.forEach(ProductMedia::clearPrimary);
            selected.selectAsPrimary();
            mediaRepository.saveAllAndFlush(media);
            auditLogService.record(
                    storeId,
                    actor.getUserId(),
                    AuditAction.PRODUCT_PRIMARY_MEDIA_CHANGED,
                    "PRODUCT_MEDIA",
                    mediaId,
                    "productId=" + productId);
        }
        return response(selected);
    }

    @Transactional
    public void delete(
            OidcUser principal, UUID storeId, UUID productId, UUID mediaId) {
        StoreMember actor = authorizationService.requireMembership(principal, storeId);
        productService.requireActiveStore(storeId);
        productRepository.findLockedByIdAndStoreId(productId, storeId)
                .filter(product -> product.getStatus() == ProductStatus.ACTIVE)
                .orElseThrow(ProductCatalogException::notFound);
        ProductMedia media = mediaRepository.findByIdAndStoreIdAndProductIdAndStatus(
                        mediaId, storeId, productId, ProductMediaStatus.ATTACHED)
                .orElseThrow(() -> new ProductCatalogException(
                        HttpStatus.NOT_FOUND, "PRODUCT_MEDIA_NOT_FOUND", "Product image not found"));
        try {
            storage.deleteObject(media.getObjectKey());
        } catch (IOException exception) {
            throw new ProductCatalogException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "PRODUCT_MEDIA_STORAGE_FAILED",
                    "The image could not be deleted");
        }
        mediaRepository.delete(media);
        mediaRepository.flush();
        auditLogService.record(
                storeId,
                actor.getUserId(),
                AuditAction.PRODUCT_MEDIA_DELETED,
                "PRODUCT_MEDIA",
                mediaId,
                "productId=" + productId + ",primary=" + media.isPrimary());
    }

    @Transactional(readOnly = true)
    public MediaContent openContent(
            OidcUser principal, UUID storeId, UUID productId, UUID mediaId) {
        authorizationService.requireMembership(principal, storeId);
        productService.requireProductForStore(storeId, productId);
        ProductMedia media = mediaRepository.findByIdAndStoreIdAndProductIdAndStatus(
                        mediaId, storeId, productId, ProductMediaStatus.ATTACHED)
                .orElseThrow(() -> new ProductCatalogException(
                        HttpStatus.NOT_FOUND, "PRODUCT_MEDIA_NOT_FOUND", "Product image not found"));
        try {
            return new MediaContent(
                    storage.openObject(media.getObjectKey()),
                    media.getContentType(),
                    media.getByteSize());
        } catch (IOException exception) {
            throw new ProductCatalogException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "PRODUCT_MEDIA_STORAGE_FAILED",
                    "The image could not be read");
        }
    }

    private void validateUploadEnvelope(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ProductCatalogException(
                    HttpStatus.BAD_REQUEST, "PRODUCT_MEDIA_EMPTY", "An image file is required");
        }
        if (file.getSize() > maximumFileBytes) {
            throw tooLarge();
        }
    }

    private ProductCatalogException tooLarge() {
        return new ProductCatalogException(
                HttpStatus.CONTENT_TOO_LARGE,
                "PRODUCT_MEDIA_TOO_LARGE",
                "Image exceeds the configured maximum size");
    }

    private void compensateStorage(UUID mediaId, String objectKey, boolean promoted) {
        try {
            if (promoted) {
                storage.deleteObject(objectKey);
            } else {
                storage.deleteTemporary(mediaId);
            }
        } catch (IOException cleanupFailure) {
            LOGGER.warn("Media compensation failed for upload {}", mediaId, cleanupFailure);
        }
    }

    private MediaResponse response(ProductMedia media) {
        String contentUrl = "/api/v1/stores/" + media.getStoreId()
                + "/products/" + media.getProductId()
                + "/media/" + media.getId() + "/content";
        return new MediaResponse(
                media.getId(),
                media.getProductId(),
                media.getContentType(),
                media.getByteSize(),
                media.isPrimary(),
                contentUrl,
                media.getCreatedAt(),
                media.getAttachedAt());
    }

    public record MediaResponse(
            UUID id,
            UUID productId,
            String contentType,
            Long byteSize,
            boolean primary,
            String contentUrl,
            OffsetDateTime createdAt,
            OffsetDateTime attachedAt) {
    }

    public record MediaContent(InputStream input, String contentType, long byteSize) {
    }
}
