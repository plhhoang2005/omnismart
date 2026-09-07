package vn.omnismart.content;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "content_approval")
public class ContentApproval {

    @Id
    private UUID id;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "content_item_id", nullable = false)
    private UUID contentItemId;

    @Column(name = "submitted_version_id", nullable = false)
    private UUID submittedVersionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ApprovalStatus status;

    @Column(name = "submitted_by_user_id")
    private UUID submittedByUserId;

    @Column(name = "reviewed_by_user_id")
    private UUID reviewedByUserId;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @Column(name = "submitted_at", nullable = false)
    private OffsetDateTime submittedAt;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    protected ContentApproval() {
    }

    public ContentApproval(
            UUID id,
            UUID storeId,
            UUID contentItemId,
            UUID submittedVersionId,
            UUID submittedByUserId) {
        this.id = id;
        this.storeId = storeId;
        this.contentItemId = contentItemId;
        this.submittedVersionId = submittedVersionId;
        this.status = ApprovalStatus.PENDING;
        this.submittedByUserId = submittedByUserId;
        this.submittedAt = OffsetDateTime.now();
    }

    public void approve(UUID reviewerUserId) {
        this.status = ApprovalStatus.APPROVED;
        this.reviewedByUserId = reviewerUserId;
        this.reviewedAt = OffsetDateTime.now();
    }

    public void reject(UUID reviewerUserId, String reason) {
        this.status = ApprovalStatus.REJECTED;
        this.reviewedByUserId = reviewerUserId;
        this.rejectionReason = reason;
        this.reviewedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getStoreId() { return storeId; }
    public UUID getContentItemId() { return contentItemId; }
    public UUID getSubmittedVersionId() { return submittedVersionId; }
    public ApprovalStatus getStatus() { return status; }
    public UUID getSubmittedByUserId() { return submittedByUserId; }
    public UUID getReviewedByUserId() { return reviewedByUserId; }
    public String getRejectionReason() { return rejectionReason; }
    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public OffsetDateTime getReviewedAt() { return reviewedAt; }
}
