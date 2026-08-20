package vn.omnismart.store;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface StoreMemberRepository extends JpaRepository<StoreMember, StoreMemberId> {

    List<StoreMember> findByUserId(UUID userId);

    List<StoreMember> findByStoreIdOrderByCreatedAtAsc(UUID storeId);

    Optional<StoreMember> findByStoreIdAndUserId(UUID storeId, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select member from StoreMember member where member.storeId = :storeId")
    List<StoreMember> lockAllByStoreId(@Param("storeId") UUID storeId);
}
