package vn.omnismart.content;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "content_item")
public class ContentItem {

    @Id
    private UUID id;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ContentChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ContentStatus status;

    @Column(name = "current_version_number", nullable = false)
    private int currentVersionNumber;

    @Version
    @Column(name = "lock_version", nullable = false)
    private long version;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ContentItem() {
    }

    public ContentItem(
            UUID id,
            UUID storeId,
            UUID productId,
            ContentChannel channel,
            UUID createdByUserId) {
        OffsetDateTime now = OffsetDateTime.now();
        this.id = id;
        this.storeId = storeId;
        this.productId = productId;
        this.channel = channel;
        this.status = ContentStatus.DRAFT;
        this.currentVersionNumber = 1;
        this.createdByUserId = createdByUserId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public int edit() {
        this.currentVersionNumber++;
        this.status = ContentStatus.DRAFT;
        this.updatedAt = OffsetDateTime.now();
        return currentVersionNumber;
    }

    public void submit() {
        this.status = ContentStatus.IN_REVIEW;
        this.updatedAt = OffsetDateTime.now();
    }

    public void approve() {
        this.status = ContentStatus.APPROVED;
        this.updatedAt = OffsetDateTime.now();
    }

    public void reject() {
        this.status = ContentStatus.REJECTED;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getStoreId() { return storeId; }
    public UUID getProductId() { return productId; }
    public ContentChannel getChannel() { return channel; }
    public ContentStatus getStatus() { return status; }
    public int getCurrentVersionNumber() { return currentVersionNumber; }
    public long getVersion() { return version; }
    public UUID getCreatedByUserId() { return createdByUserId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
