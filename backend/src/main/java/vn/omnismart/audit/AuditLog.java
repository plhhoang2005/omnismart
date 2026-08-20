package vn.omnismart.audit;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    private UUID id;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 48)
    private AuditAction action;

    @Column(name = "resource_type", nullable = false, length = 48)
    private String resourceType;

    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    @Column(length = 500)
    private String details;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected AuditLog() {
    }

    public AuditLog(
            UUID id,
            UUID storeId,
            UUID actorUserId,
            AuditAction action,
            String resourceType,
            UUID resourceId,
            String details) {
        this.id = id;
        this.storeId = storeId;
        this.actorUserId = actorUserId;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.details = details;
        this.createdAt = OffsetDateTime.now();
    }

    public AuditAction getAction() {
        return action;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public String getDetails() {
        return details;
    }
}
