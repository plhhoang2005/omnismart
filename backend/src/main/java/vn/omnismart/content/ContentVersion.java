package vn.omnismart.content;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "content_version")
public class ContentVersion {

    @Id
    private UUID id;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "content_item_id", nullable = false)
    private UUID contentItemId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(nullable = false, length = 10000)
    private String body;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected ContentVersion() {
    }

    public ContentVersion(
            UUID id,
            UUID storeId,
            UUID contentItemId,
            int versionNumber,
            String body,
            UUID createdByUserId) {
        this.id = id;
        this.storeId = storeId;
        this.contentItemId = contentItemId;
        this.versionNumber = versionNumber;
        this.body = body;
        this.createdByUserId = createdByUserId;
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getStoreId() { return storeId; }
    public UUID getContentItemId() { return contentItemId; }
    public int getVersionNumber() { return versionNumber; }
    public String getBody() { return body; }
    public UUID getCreatedByUserId() { return createdByUserId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
