package vn.omnismart.store;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "store")
public class Store {

    @Id
    private UUID id;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, length = 100, unique = true)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private StoreStatus status;

    @Column(name = "onboarding_completed", nullable = false)
    private boolean onboardingCompleted;

    @Column(name = "archived_at")
    private OffsetDateTime archivedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Store() {
    }

    public Store(UUID id, String name, String slug) {
        this(id, name, slug, true);
    }

    private Store(UUID id, String name, String slug, boolean onboardingCompleted) {
        OffsetDateTime now = OffsetDateTime.now();
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.status = StoreStatus.ACTIVE;
        this.onboardingCompleted = onboardingCompleted;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Store pendingOnboarding(UUID id, String name, String slug) {
        return new Store(id, name, slug, false);
    }

    public void confirmDetails(String name) {
        this.name = name;
        this.onboardingCompleted = true;
        this.updatedAt = OffsetDateTime.now();
    }

    public void changeStatus(StoreStatus status) {
        this.status = status;
        this.archivedAt = status == StoreStatus.ARCHIVED ? OffsetDateTime.now() : null;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public StoreStatus getStatus() {
        return status;
    }

    public boolean isOnboardingCompleted() {
        return onboardingCompleted;
    }

    public OffsetDateTime getArchivedAt() {
        return archivedAt;
    }
}
