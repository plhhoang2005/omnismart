package vn.omnismart.membership;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import vn.omnismart.store.StoreRole;

@Entity
@Table(name = "membership_invitation")
public class MembershipInvitation {

    @Id
    private UUID id;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(name = "pending_email", length = 320)
    private String pendingEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private StoreRole role;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InvitationStatus status;

    @Column(name = "invited_by_user_id")
    private UUID invitedByUserId;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "responded_at")
    private OffsetDateTime respondedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected MembershipInvitation() {
    }

    public MembershipInvitation(
            UUID id,
            UUID storeId,
            String email,
            StoreRole role,
            String tokenHash,
            UUID invitedByUserId,
            OffsetDateTime expiresAt) {
        this.id = id;
        this.storeId = storeId;
        this.email = email;
        this.pendingEmail = email;
        this.role = role;
        this.tokenHash = tokenHash;
        this.status = InvitationStatus.PENDING;
        this.invitedByUserId = invitedByUserId;
        this.expiresAt = expiresAt;
        this.createdAt = OffsetDateTime.now();
    }

    public boolean isExpiredAt(OffsetDateTime now) {
        return !now.isBefore(expiresAt);
    }

    public void accept(OffsetDateTime now) {
        complete(InvitationStatus.ACCEPTED, now);
    }

    public void decline(OffsetDateTime now) {
        complete(InvitationStatus.DECLINED, now);
    }

    public void expire(OffsetDateTime now) {
        complete(InvitationStatus.EXPIRED, now);
    }

    public void revoke(OffsetDateTime now) {
        complete(InvitationStatus.REVOKED, now);
    }

    private void complete(InvitationStatus newStatus, OffsetDateTime now) {
        if (status != InvitationStatus.PENDING) {
            throw new IllegalStateException("Invitation has already been used");
        }
        this.status = newStatus;
        this.pendingEmail = null;
        this.respondedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getStoreId() {
        return storeId;
    }

    public String getEmail() {
        return email;
    }

    public StoreRole getRole() {
        return role;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public InvitationStatus getStatus() {
        return status;
    }

    public UUID getInvitedByUserId() {
        return invitedByUserId;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public OffsetDateTime getRespondedAt() {
        return respondedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
