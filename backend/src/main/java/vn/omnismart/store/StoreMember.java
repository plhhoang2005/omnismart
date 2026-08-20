package vn.omnismart.store;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "store_member")
@IdClass(StoreMemberId.class)
public class StoreMember {

    @Id
    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private StoreRole role;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected StoreMember() {
    }

    public StoreMember(UUID storeId, UUID userId, StoreRole role) {
        this.storeId = storeId;
        this.userId = userId;
        this.role = role;
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void changeRole(StoreRole role) {
        this.role = role;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getStoreId() {
        return storeId;
    }

    public UUID getUserId() {
        return userId;
    }

    public StoreRole getRole() {
        return role;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
