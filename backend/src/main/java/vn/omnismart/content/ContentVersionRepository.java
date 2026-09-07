package vn.omnismart.content;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentVersionRepository extends JpaRepository<ContentVersion, UUID> {

    Optional<ContentVersion> findByContentItemIdAndStoreIdAndVersionNumber(
            UUID contentItemId,
            UUID storeId,
            int versionNumber);

    List<ContentVersion> findByContentItemIdAndStoreIdOrderByVersionNumberDesc(
            UUID contentItemId,
            UUID storeId);
}
