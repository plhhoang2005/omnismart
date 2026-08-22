package vn.omnismart.catalog;

import java.math.BigDecimal;
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
@Table(name = "product")
public class Product {

    @Id
    private UUID id;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(nullable = false, length = 64)
    private String sku;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 5000)
    private String description;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private ProductCurrency currency;

    @Column(name = "inventory_quantity", nullable = false)
    private int inventoryQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ProductStatus status;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Product() {
    }

    public Product(
            UUID id,
            UUID storeId,
            String sku,
            String name,
            String description,
            BigDecimal price,
            ProductCurrency currency,
            int inventoryQuantity) {
        OffsetDateTime now = OffsetDateTime.now();
        this.id = id;
        this.storeId = storeId;
        this.sku = sku;
        this.name = name;
        this.description = description;
        this.price = price;
        this.currency = currency;
        this.inventoryQuantity = inventoryQuantity;
        this.status = ProductStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(
            String sku,
            String name,
            String description,
            boolean updateDescription,
            BigDecimal price,
            ProductCurrency currency,
            Integer inventoryQuantity) {
        if (sku != null) {
            this.sku = sku;
        }
        if (name != null) {
            this.name = name;
        }
        if (updateDescription) {
            this.description = description;
        }
        if (price != null) {
            this.price = price;
        }
        if (currency != null) {
            this.currency = currency;
        }
        if (inventoryQuantity != null) {
            this.inventoryQuantity = inventoryQuantity;
        }
        this.updatedAt = OffsetDateTime.now();
    }

    public void archive() {
        this.status = ProductStatus.ARCHIVED;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getStoreId() { return storeId; }
    public String getSku() { return sku; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public BigDecimal getPrice() { return price; }
    public ProductCurrency getCurrency() { return currency; }
    public int getInventoryQuantity() { return inventoryQuantity; }
    public ProductStatus getStatus() { return status; }
    public long getVersion() { return version; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
