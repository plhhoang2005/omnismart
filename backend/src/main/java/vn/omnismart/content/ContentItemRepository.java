package vn.omnismart.content;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContentItemRepository extends JpaRepository<ContentItem, UUID> {

    Optional<ContentItem> findByIdAndStoreId(UUID id, UUID storeId);

    @Query("""
            SELECT item FROM ContentItem item
            WHERE item.storeId = :storeId
              AND (:productId IS NULL OR item.productId = :productId)
              AND (:status IS NULL OR item.status = :status)
              AND (:channel IS NULL OR item.channel = :channel)
            """)
    Page<ContentItem> search(
            @Param("storeId") UUID storeId,
            @Param("productId") UUID productId,
            @Param("status") ContentStatus status,
            @Param("channel") ContentChannel channel,
            Pageable pageable);
}
