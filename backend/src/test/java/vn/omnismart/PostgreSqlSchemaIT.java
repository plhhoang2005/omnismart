package vn.omnismart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest
class PostgreSqlSchemaIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("omnismart_it")
            .withUsername("omnismart_it")
            .withPassword("omnismart_it");

    @DynamicPropertySource
    static void configurePostgreSql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void flywayAppliesEveryMigrationOnPostgreSql() {
        Integer appliedMigrations = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE",
                Integer.class);
        String currentVersion = jdbcTemplate.queryForObject(
                "SELECT version FROM flyway_schema_history "
                        + "WHERE success = TRUE AND version IS NOT NULL "
                        + "ORDER BY installed_rank DESC LIMIT 1",
                String.class);

        assertThat(appliedMigrations).isEqualTo(6);
        assertThat(currentVersion).isEqualTo("6");
    }

    @Test
    void databaseAllowsSameSkuAcrossStoresButRejectsDuplicateWithinStore() {
        UUID firstStore = insertStore("first-store");
        UUID secondStore = insertStore("second-store");

        insertProduct(firstStore, "SHARED-SKU");
        insertProduct(secondStore, "SHARED-SKU");

        assertThatThrownBy(() -> insertProduct(firstStore, "SHARED-SKU"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsMediaLinkedToAProductFromAnotherStore() {
        UUID mediaStore = insertStore("media-store");
        UUID productStore = insertStore("product-store");
        UUID productId = insertProduct(productStore, "TENANT-PRODUCT");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO product_media "
                        + "(id, store_id, product_id, object_key, content_type, byte_size, "
                        + "status, is_primary, attached_at) "
                        + "VALUES (?, ?, ?, ?, 'image/png', 1, 'ATTACHED', FALSE, CURRENT_TIMESTAMP)",
                UUID.randomUUID(),
                mediaStore,
                productId,
                "cross-tenant/should-not-exist"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID insertStore(String slug) {
        UUID storeId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO store (id, name, slug) VALUES (?, ?, ?)",
                storeId,
                slug,
                slug);
        return storeId;
    }

    private UUID insertProduct(UUID storeId, String sku) {
        UUID productId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO product "
                        + "(id, store_id, sku, name, price, currency, inventory_quantity, status) "
                        + "VALUES (?, ?, ?, ?, ?, 'VND', 0, 'ACTIVE')",
                productId,
                storeId,
                sku,
                "Integration product",
                new BigDecimal("1000.00"));
        return productId;
    }
}
