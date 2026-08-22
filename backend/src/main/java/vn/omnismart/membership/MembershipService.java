package vn.omnismart.membership;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import vn.omnismart.audit.AuditAction;
import vn.omnismart.audit.AuditLogService;
import vn.omnismart.identity.AppUser;
import vn.omnismart.identity.AppUserRepository;
import vn.omnismart.store.Store;
import vn.omnismart.store.StoreAuthorizationService;
import vn.omnismart.store.StoreMember;
import vn.omnismart.store.StoreMemberRepository;
import vn.omnismart.store.StoreRepository;
import vn.omnismart.store.StoreRole;
import vn.omnismart.store.StoreStatus;

@Service
public class MembershipService {

    private final StoreAuthorizationService authorizationService;
    private final StoreMemberRepository memberRepository;
    private final StoreRepository storeRepository;
    private final AppUserRepository userRepository;
    private final AuditLogService auditLogService;

    public MembershipService(
            StoreAuthorizationService authorizationService,
            StoreMemberRepository memberRepository,
            StoreRepository storeRepository,
            AppUserRepository userRepository,
            AuditLogService auditLogService) {
        this.authorizationService = authorizationService;
        this.memberRepository = memberRepository;
        this.storeRepository = storeRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> list(OidcUser principal, UUID storeId) {
        authorizationService.requireOwner(principal, storeId);
        List<StoreMember> memberships = memberRepository.findByStoreIdOrderByCreatedAtAsc(storeId);
        Map<UUID, AppUser> users = userRepository.findAllById(
                        memberships.stream().map(StoreMember::getUserId).toList())
                .stream()
                .collect(Collectors.toMap(AppUser::getId, Function.identity()));

        return memberships.stream()
                .map(membership -> response(membership, requireUser(users, membership.getUserId())))
                .sorted(Comparator.comparing(MemberResponse::displayName))
                .toList();
    }

    @Transactional
    public MemberResponse changeRole(
            OidcUser principal,
            UUID storeId,
            UUID targetUserId,
            StoreRole requestedRole,
            String confirmationName) {
        StoreMember actor = authorizationService.requireOwner(principal, storeId);
        Store store = requireActiveStore(storeId);
        requireStoreNameConfirmation(store, confirmationName);

        List<StoreMember> lockedMemberships = memberRepository.lockAllByStoreId(storeId);
        StoreMember target = lockedMemberships.stream()
                .filter(member -> member.getUserId().equals(targetUserId))
                .findFirst()
                .orElseThrow(MembershipService::memberNotFound);
        StoreRole previousRole = target.getRole();
        if (previousRole == requestedRole) {
            return response(target, requireUser(targetUserId));
        }
        preventRemovingLastOwner(lockedMemberships, target, requestedRole);

        target.changeRole(requestedRole);
        memberRepository.save(target);
        auditLogService.record(
                storeId,
                actor.getUserId(),
                AuditAction.MEMBER_ROLE_CHANGED,
                "STORE_MEMBER",
                targetUserId,
                "from=" + previousRole + ",to=" + requestedRole);
        return response(target, requireUser(targetUserId));
    }

    @Transactional
    public void revoke(
            OidcUser principal,
            UUID storeId,
            UUID targetUserId,
            String confirmationName) {
        StoreMember actor = authorizationService.requireOwner(principal, storeId);
        Store store = requireActiveStore(storeId);
        requireStoreNameConfirmation(store, confirmationName);

        List<StoreMember> lockedMemberships = memberRepository.lockAllByStoreId(storeId);
        StoreMember target = lockedMemberships.stream()
                .filter(member -> member.getUserId().equals(targetUserId))
                .findFirst()
                .orElseThrow(MembershipService::memberNotFound);
        preventRemovingLastOwner(lockedMemberships, target, null);

        memberRepository.delete(target);
        auditLogService.record(
                storeId,
                actor.getUserId(),
                AuditAction.MEMBER_REVOKED,
                "STORE_MEMBER",
                targetUserId,
                "role=" + target.getRole());
    }

    private void preventRemovingLastOwner(
            List<StoreMember> memberships,
            StoreMember target,
            StoreRole requestedRole) {
        boolean removesOwner = target.getRole() == StoreRole.OWNER
                && requestedRole != StoreRole.OWNER;
        if (!removesOwner) {
            return;
        }
        long ownerCount = memberships.stream()
                .filter(member -> member.getRole() == StoreRole.OWNER)
                .count();
        if (ownerCount <= 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A store must keep at least one Owner");
        }
    }

    private Store requireActiveStore(UUID storeId) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Store not found"));
        if (store.getStatus() != StoreStatus.ACTIVE) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Archived stores cannot change memberships");
        }
        return store;
    }

    private void requireStoreNameConfirmation(Store store, String confirmationName) {
        if (!store.getName().equals(confirmationName)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Store name confirmation does not match");
        }
    }

    private AppUser requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Membership references a missing user"));
    }

    private AppUser requireUser(Map<UUID, AppUser> users, UUID userId) {
        AppUser user = users.get(userId);
        if (user == null) {
            throw new IllegalStateException("Membership references a missing user");
        }
        return user;
    }

    private MemberResponse response(StoreMember membership, AppUser user) {
        return new MemberResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                membership.getRole(),
                membership.getCreatedAt(),
                membership.getUpdatedAt());
    }

    private static ResponseStatusException memberNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found");
    }

    public record MemberResponse(
            UUID userId,
            String email,
            String displayName,
            StoreRole role,
            OffsetDateTime joinedAt,
            OffsetDateTime updatedAt) {

        @Override
        public String toString() {
            return "MemberResponse[userId=" + userId
                    + ", email=[REDACTED]"
                    + ", displayName=[REDACTED]"
                    + ", role=" + role
                    + ", joinedAt=" + joinedAt
                    + ", updatedAt=" + updatedAt + "]";
        }
    }
}
