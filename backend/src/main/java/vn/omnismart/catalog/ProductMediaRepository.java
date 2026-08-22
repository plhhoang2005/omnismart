package vn.omnismart.catalog;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductMediaRepository extends JpaRepository<ProductMedia, UUID> {

    long countByStoreIdAndProductIdAndStatus(
            UUID storeId, UUID productId, ProductMediaStatus status);

    List<ProductMedia> findByStoreIdAndProductIdAndStatusOrderByCreatedAtAsc(
            UUID storeId, UUID productId, ProductMediaStatus status);

    Optional<ProductMedia> findByIdAndStoreIdAndProductIdAndStatus(
            UUID id, UUID storeId, UUID productId, ProductMediaStatus status);

    boolean existsByObjectKey(String objectKey);

    List<ProductMedia> findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
            ProductMediaStatus status, OffsetDateTime cutoff, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT media FROM ProductMedia media
            WHERE media.storeId = :storeId
              AND media.productId = :productId
              AND media.status = 'ATTACHED'
            """)
    List<ProductMedia> lockAttachedMedia(
            @Param("storeId") UUID storeId,
            @Param("productId") UUID productId);
}
