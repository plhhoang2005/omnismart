package vn.omnismart.store;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreMemberRepository extends JpaRepository<StoreMember, StoreMemberId> {

    List<StoreMember> findByUserId(UUID userId);

    Optional<StoreMember> findByStoreIdAndUserId(UUID storeId, UUID userId);
}
