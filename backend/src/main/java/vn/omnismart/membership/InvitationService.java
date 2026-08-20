package vn.omnismart.membership;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
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
import vn.omnismart.store.StoreOperationGuard;
import vn.omnismart.store.StoreRepository;
import vn.omnismart.store.StoreRole;

@Service
public class InvitationService {

    private static final int TOKEN_BYTES = 32;
    private static final Duration MAX_INVITATION_TTL = Duration.ofDays(30);

    private final MembershipInvitationRepository invitationRepository;
    private final StoreAuthorizationService authorizationService;
    private final StoreMemberRepository memberRepository;
    private final StoreRepository storeRepository;
    private final StoreOperationGuard storeOperationGuard;
    private final AppUserRepository userRepository;
    private final AuditLogService auditLogService;
    private final Duration invitationTtl;
    private final Clock clock;
    private final SecureRandom secureRandom;

    @Autowired
    public InvitationService(
            MembershipInvitationRepository invitationRepository,
            StoreAuthorizationService authorizationService,
            StoreMemberRepository memberRepository,
            StoreRepository storeRepository,
            StoreOperationGuard storeOperationGuard,
            AppUserRepository userRepository,
            AuditLogService auditLogService,
            @Value("${omnismart.membership.invitation-ttl:PT72H}") Duration invitationTtl) {
        this(
                invitationRepository,
                authorizationService,
                memberRepository,
                storeRepository,
                storeOperationGuard,
                userRepository,
                auditLogService,
                invitationTtl,
                Clock.systemUTC(),
                new SecureRandom());
    }

    InvitationService(
            MembershipInvitationRepository invitationRepository,
            StoreAuthorizationService authorizationService,
            StoreMemberRepository memberRepository,
            StoreRepository storeRepository,
            StoreOperationGuard storeOperationGuard,
            AppUserRepository userRepository,
            AuditLogService auditLogService,
            Duration invitationTtl,
            Clock clock,
            SecureRandom secureRandom) {
        if (invitationTtl.isZero()
                || invitationTtl.isNegative()
                || invitationTtl.compareTo(MAX_INVITATION_TTL) > 0) {
            throw new IllegalArgumentException("Invitation TTL must be between 1 second and 30 days");
        }
        this.invitationRepository = invitationRepository;
        this.authorizationService = authorizationService;
        this.memberRepository = memberRepository;
        this.storeRepository = storeRepository;
        this.storeOperationGuard = storeOperationGuard;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.invitationTtl = invitationTtl;
        this.clock = clock;
        this.secureRandom = secureRandom;
    }

    @Transactional
    public CreatedInvitationResponse create(
            OidcUser principal,
            UUID storeId,
            String requestedEmail,
            StoreRole role,
            String confirmationName) {
        StoreMember actor = authorizationService.requireOwner(principal, storeId);
        Store store = requireActiveStore(storeId);
        String email = normalizeEmail(requestedEmail);
        if (role == StoreRole.OWNER && !store.getName().equals(confirmationName)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Store name confirmation is required when inviting an Owner");
        }

        rejectExistingMember(storeId, email);
        expireOrRejectExistingInvitation(storeId, email, actor.getUserId());

        String rawToken = generateToken();
        OffsetDateTime expiresAt = now().plus(invitationTtl);
        MembershipInvitation invitation = new MembershipInvitation(
                UUID.randomUUID(),
                storeId,
                email,
                role,
                hashToken(rawToken),
                actor.getUserId(),
                expiresAt);
        try {
            invitationRepository.saveAndFlush(invitation);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A pending invitation already exists",
                    exception);
        }
        auditLogService.record(
                storeId,
                actor.getUserId(),
                AuditAction.INVITATION_CREATED,
                "MEMBERSHIP_INVITATION",
                invitation.getId(),
                "role=" + role);
        return new CreatedInvitationResponse(
                invitation.getId(),
                storeId,
                store.getName(),
                email,
                role,
                InvitationStatus.PENDING,
                expiresAt,
                rawToken,
                "MANUAL");
    }

    @Transactional(readOnly = true)
    public List<InvitationResponse> listForStore(OidcUser principal, UUID storeId) {
        authorizationService.requireOwner(principal, storeId);
        Store store = requireStore(storeId);
        OffsetDateTime now = now();
        return invitationRepository.findByStoreIdOrderByCreatedAtDesc(storeId).stream()
                .map(invitation -> response(invitation, store.getName(), effectiveStatus(invitation, now)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InvitationResponse> listForCurrentUser(OidcUser principal) {
        AppUser user = authorizationService.requireUser(principal);
        OffsetDateTime now = now();
        return invitationRepository
                .findByEmailAndStatusOrderByCreatedAtDesc(
                        user.getEmail().toLowerCase(Locale.ROOT),
                        InvitationStatus.PENDING)
                .stream()
                .map(invitation -> response(
                        invitation,
                        requireStore(invitation.getStoreId()).getName(),
                        effectiveStatus(invitation, now)))
                .toList();
    }

    @Transactional(noRollbackFor = InvitationExpiredException.class)
    public InvitationResponse accept(
            OidcUser principal,
            UUID invitationId,
            String rawToken) {
        return respond(principal, invitationId, rawToken, true);
    }

    @Transactional(noRollbackFor = InvitationExpiredException.class)
    public InvitationResponse decline(
            OidcUser principal,
            UUID invitationId,
            String rawToken) {
        return respond(principal, invitationId, rawToken, false);
    }

    @Transactional(noRollbackFor = InvitationExpiredException.class)
    public void revoke(
            OidcUser principal,
            UUID storeId,
            UUID invitationId,
            String confirmationEmail) {
        StoreMember actor = authorizationService.requireOwner(principal, storeId);
        requireActiveStore(storeId);
        MembershipInvitation invitation = invitationRepository.findLockedById(invitationId)
                .filter(candidate -> candidate.getStoreId().equals(storeId))
                .orElseThrow(InvitationService::invitationNotFound);
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invitation has already been used");
        }
        OffsetDateTime now = now();
        if (invitation.isExpiredAt(now)) {
            invitation.expire(now);
            invitationRepository.save(invitation);
            auditLogService.record(
                    storeId,
                    actor.getUserId(),
                    AuditAction.INVITATION_EXPIRED,
                    "MEMBERSHIP_INVITATION",
                    invitationId,
                    "role=" + invitation.getRole());
            throw new InvitationExpiredException();
        }
        if (!invitation.getEmail().equals(normalizeEmail(confirmationEmail))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invitation email confirmation does not match");
        }
        invitation.revoke(now);
        invitationRepository.save(invitation);
        auditLogService.record(
                storeId,
                actor.getUserId(),
                AuditAction.INVITATION_REVOKED,
                "MEMBERSHIP_INVITATION",
                invitationId,
                "role=" + invitation.getRole());
    }

    private InvitationResponse respond(
            OidcUser principal,
            UUID invitationId,
            String rawToken,
            boolean accept) {
        AppUser actor = authorizationService.requireUser(principal);
        MembershipInvitation invitation = invitationRepository.findLockedById(invitationId)
                .orElseThrow(InvitationService::invitationNotFound);
        if (!invitation.getEmail().equals(actor.getEmail().toLowerCase(Locale.ROOT))
                || !tokenMatches(rawToken, invitation.getTokenHash())) {
            throw invitationNotFound();
        }
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invitation has already been used");
        }

        OffsetDateTime now = now();
        if (invitation.isExpiredAt(now)) {
            invitation.expire(now);
            invitationRepository.save(invitation);
            auditLogService.record(
                    invitation.getStoreId(),
                    actor.getId(),
                    AuditAction.INVITATION_EXPIRED,
                    "MEMBERSHIP_INVITATION",
                    invitation.getId(),
                    "role=" + invitation.getRole());
            throw new InvitationExpiredException();
        }

        Store store = requireActiveStore(invitation.getStoreId());
        if (accept) {
            if (memberRepository
                    .findByStoreIdAndUserId(invitation.getStoreId(), actor.getId())
                    .isPresent()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "User is already a member");
            }
            memberRepository.save(new StoreMember(
                    invitation.getStoreId(), actor.getId(), invitation.getRole()));
            invitation.accept(now);
            auditLogService.record(
                    invitation.getStoreId(),
                    actor.getId(),
                    AuditAction.INVITATION_ACCEPTED,
                    "MEMBERSHIP_INVITATION",
                    invitation.getId(),
                    "role=" + invitation.getRole());
        } else {
            invitation.decline(now);
            auditLogService.record(
                    invitation.getStoreId(),
                    actor.getId(),
                    AuditAction.INVITATION_DECLINED,
                    "MEMBERSHIP_INVITATION",
                    invitation.getId(),
                    "role=" + invitation.getRole());
        }
        invitationRepository.save(invitation);
        return response(invitation, store.getName(), invitation.getStatus());
    }

    private void rejectExistingMember(UUID storeId, String email) {
        userRepository.findByEmailIgnoreCase(email)
                .map(AppUser::getId)
                .flatMap(userId -> memberRepository.findByStoreIdAndUserId(storeId, userId))
                .ifPresent(member -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "User is already a member");
                });
    }

    private void expireOrRejectExistingInvitation(UUID storeId, String email, UUID actorUserId) {
        invitationRepository.findByStoreIdAndPendingEmail(storeId, email)
                .ifPresent(existing -> {
                    OffsetDateTime now = now();
                    if (!existing.isExpiredAt(now)) {
                        throw new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "A pending invitation already exists");
                    }
                    existing.expire(now);
                    invitationRepository.saveAndFlush(existing);
                    auditLogService.record(
                            storeId,
                            actorUserId,
                            AuditAction.INVITATION_EXPIRED,
                            "MEMBERSHIP_INVITATION",
                            existing.getId(),
                            "role=" + existing.getRole());
                });
    }

    private Store requireActiveStore(UUID storeId) {
        return storeOperationGuard.requireOperational(storeId);
    }

    private Store requireStore(UUID storeId) {
        return storeRepository.findById(storeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Store not found"));
    }

    private InvitationStatus effectiveStatus(
            MembershipInvitation invitation,
            OffsetDateTime now) {
        if (invitation.getStatus() == InvitationStatus.PENDING && invitation.isExpiredAt(now)) {
            return InvitationStatus.EXPIRED;
        }
        return invitation.getStatus();
    }

    private InvitationResponse response(
            MembershipInvitation invitation,
            String storeName,
            InvitationStatus status) {
        return new InvitationResponse(
                invitation.getId(),
                invitation.getStoreId(),
                storeName,
                invitation.getEmail(),
                invitation.getRole(),
                status,
                invitation.getExpiresAt(),
                invitation.getRespondedAt(),
                invitation.getCreatedAt());
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private String generateToken() {
        byte[] token = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

    private String hashToken(String token) {
        if (token == null || token.isBlank()) {
            throw invitationNotFound();
        }
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private boolean tokenMatches(String rawToken, String expectedHash) {
        byte[] actual = hashToken(rawToken).getBytes(StandardCharsets.US_ASCII);
        byte[] expected = expectedHash.getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(actual, expected);
    }

    private String normalizeEmail(String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required");
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static ResponseStatusException invitationNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation not found");
    }

    public record CreatedInvitationResponse(
            UUID id,
            UUID storeId,
            String storeName,
            String email,
            StoreRole role,
            InvitationStatus status,
            OffsetDateTime expiresAt,
            String invitationToken,
            String delivery) {

        @Override
        public String toString() {
            return "CreatedInvitationResponse[id=" + id
                    + ", storeId=" + storeId
                    + ", email=[REDACTED]"
                    + ", role=" + role
                    + ", status=" + status
                    + ", expiresAt=" + expiresAt
                    + ", invitationToken=[REDACTED]"
                    + ", delivery=" + delivery + "]";
        }
    }

    public record InvitationResponse(
            UUID id,
            UUID storeId,
            String storeName,
            String email,
            StoreRole role,
            InvitationStatus status,
            OffsetDateTime expiresAt,
            OffsetDateTime respondedAt,
            OffsetDateTime createdAt) {

        @Override
        public String toString() {
            return "InvitationResponse[id=" + id
                    + ", storeId=" + storeId
                    + ", email=[REDACTED]"
                    + ", role=" + role
                    + ", status=" + status
                    + ", expiresAt=" + expiresAt
                    + ", respondedAt=" + respondedAt
                    + ", createdAt=" + createdAt + "]";
        }
    }
}
