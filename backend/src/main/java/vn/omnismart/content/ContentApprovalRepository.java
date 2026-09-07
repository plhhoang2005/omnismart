package vn.omnismart.content;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentApprovalRepository extends JpaRepository<ContentApproval, UUID> {

    Optional<ContentApproval> findFirstByContentItemIdAndStoreIdAndStatusOrderBySubmittedAtDesc(
            UUID contentItemId,
            UUID storeId,
            ApprovalStatus status);

    List<ContentApproval> findByContentItemIdAndStoreIdOrderBySubmittedAtDesc(
            UUID contentItemId,
            UUID storeId);
}
