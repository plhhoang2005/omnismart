package vn.omnismart.catalog;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "product_media")
public class ProductMedia {

    @Id
    private UUID id;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "object_key", nullable = false, length = 500, unique = true)
    private String objectKey;

    @Column(name = "content_type", length = 32)
    private String contentType;

    @Column(name = "byte_size")
    private Long byteSize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ProductMediaStatus status;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "attached_at")
    private OffsetDateTime attachedAt;

    protected ProductMedia() {
    }

    public ProductMedia(UUID id, UUID storeId, String objectKey) {
        this.id = id;
        this.storeId = storeId;
        this.objectKey = objectKey;
        this.status = ProductMediaStatus.TEMPORARY;
        this.createdAt = OffsetDateTime.now();
    }

    public void attach(UUID productId, String contentType, long byteSize, boolean primary) {
        if (status != ProductMediaStatus.TEMPORARY) {
            throw new IllegalStateException("Only temporary media can be attached");
        }
        this.productId = productId;
        this.contentType = contentType;
        this.byteSize = byteSize;
        this.primary = primary;
        this.status = ProductMediaStatus.ATTACHED;
        this.attachedAt = OffsetDateTime.now();
    }

    public void selectAsPrimary() { this.primary = true; }
    public void clearPrimary() { this.primary = false; }

    public UUID getId() { return id; }
    public UUID getStoreId() { return storeId; }
    public UUID getProductId() { return productId; }
    public String getObjectKey() { return objectKey; }
    public String getContentType() { return contentType; }
    public Long getByteSize() { return byteSize; }
    public ProductMediaStatus getStatus() { return status; }
    public boolean isPrimary() { return primary; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getAttachedAt() { return attachedAt; }
}
