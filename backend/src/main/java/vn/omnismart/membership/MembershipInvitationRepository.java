package vn.omnismart.membership;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MembershipInvitationRepository extends JpaRepository<MembershipInvitation, UUID> {

    Optional<MembershipInvitation> findByStoreIdAndPendingEmail(UUID storeId, String pendingEmail);

    List<MembershipInvitation> findByStoreIdOrderByCreatedAtDesc(UUID storeId);

    List<MembershipInvitation> findByEmailAndStatusOrderByCreatedAtDesc(
            String email,
            InvitationStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invitation from MembershipInvitation invitation where invitation.id = :id")
    Optional<MembershipInvitation> findLockedById(@Param("id") UUID id);
}
