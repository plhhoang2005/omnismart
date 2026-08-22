package vn.omnismart.catalog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findByIdAndStoreId(UUID id, UUID storeId);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT product FROM Product product WHERE product.id = :id AND product.storeId = :storeId")
    Optional<Product> findLockedByIdAndStoreId(
            @Param("id") UUID id,
            @Param("storeId") UUID storeId);

    boolean existsByStoreIdAndSku(UUID storeId, String sku);

    @Query("""
            SELECT product FROM Product product
            WHERE product.storeId = :storeId
              AND product.status IN :statuses
              AND (:search = ''
                   OR LOWER(product.name) LIKE :search ESCAPE '\\'
                   OR LOWER(product.sku) LIKE :search ESCAPE '\\')
            """)
    Page<Product> search(
            @Param("storeId") UUID storeId,
            @Param("statuses") List<ProductStatus> statuses,
            @Param("search") String search,
            Pageable pageable);
}
