package vn.omnismart.store;

import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import vn.omnismart.identity.AppUser;
import vn.omnismart.audit.AuditAction;
import vn.omnismart.audit.AuditLogService;

@Service
public class StoreService {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_SLUG_CHARACTERS = Pattern.compile("[^a-z0-9]+");

    private final StoreRepository storeRepository;
    private final StoreMemberRepository memberRepository;
    private final StoreAuthorizationService authorizationService;
    private final AuditLogService auditLogService;

    public StoreService(
            StoreRepository storeRepository,
            StoreMemberRepository memberRepository,
            StoreAuthorizationService authorizationService,
            AuditLogService auditLogService) {
        this.storeRepository = storeRepository;
        this.memberRepository = memberRepository;
        this.authorizationService = authorizationService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public StoreResponse create(OidcUser principal, String requestedName) {
        AppUser user = authorizationService.requireUser(principal);
        String name = normalizeName(requestedName);
        Store store = storeRepository.save(new Store(UUID.randomUUID(), name, uniqueSlug(name)));
        memberRepository.save(new StoreMember(store.getId(), user.getId(), StoreRole.OWNER));
        auditLogService.record(
                store.getId(), user.getId(), AuditAction.STORE_CREATED,
                "STORE", store.getId(), "status=" + store.getStatus());
        return response(store, StoreRole.OWNER);
    }

    @Transactional(readOnly = true)
    public List<StoreResponse> list(OidcUser principal) {
        AppUser user = authorizationService.requireUser(principal);
        return memberRepository.findByUserId(user.getId()).stream()
                .map(membership -> response(requireStore(membership.getStoreId()), membership.getRole()))
                .sorted(Comparator.comparing(StoreResponse::name))
                .toList();
    }

    @Transactional(readOnly = true)
    public StoreResponse get(OidcUser principal, UUID storeId) {
        StoreMember membership = authorizationService.requireMembership(principal, storeId);
        return response(requireStore(storeId), membership.getRole());
    }

    @Transactional
    public StoreResponse update(
            OidcUser principal,
            UUID storeId,
            String requestedName,
            StoreStatus requestedStatus,
            String confirmationName) {
        StoreMember membership = authorizationService.requireOwner(principal, storeId);
        Store store = requireStore(storeId);
        boolean wasOnboardingCompleted = store.isOnboardingCompleted();
        StoreStatus previousStatus = store.getStatus();

        if (requestedName == null && requestedStatus == null) {
            throw badRequest("At least one of name or status is required");
        }
        if (requestedStatus == StoreStatus.ARCHIVED && requestedName != null) {
            throw badRequest("Rename and archive must be confirmed in separate requests");
        }
        if (!store.isOnboardingCompleted() && requestedName == null) {
            throw badRequest("Confirm the store name before changing its status");
        }

        if (requestedName != null) {
            store.confirmDetails(normalizeName(requestedName));
        }
        if (requestedStatus != null && requestedStatus != store.getStatus()) {
            if (requestedStatus == StoreStatus.ARCHIVED
                    && !store.getName().equals(confirmationName)) {
                throw badRequest("Store name confirmation does not match");
            }
            store.changeStatus(requestedStatus);
        }

        Store saved = storeRepository.save(store);
        if (requestedName != null) {
            auditLogService.record(
                    storeId,
                    membership.getUserId(),
                    wasOnboardingCompleted
                            ? AuditAction.STORE_UPDATED
                            : AuditAction.STORE_ONBOARDING_COMPLETED,
                    "STORE",
                    storeId,
                    "field=name");
        }
        if (requestedStatus != null && requestedStatus != previousStatus) {
            auditLogService.record(
                    storeId,
                    membership.getUserId(),
                    requestedStatus == StoreStatus.ARCHIVED
                            ? AuditAction.STORE_ARCHIVED
                            : AuditAction.STORE_REACTIVATED,
                    "STORE",
                    storeId,
                    "status=" + requestedStatus);
        }
        return response(saved, membership.getRole());
    }

    private Store requireStore(UUID storeId) {
        return storeRepository.findById(storeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Store not found"));
    }

    private String uniqueSlug(String name) {
        String base = slugify(name);
        for (int attempt = 0; attempt < 5; attempt++) {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            String candidate = base + "-" + suffix;
            if (!storeRepository.existsBySlug(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not allocate a unique store slug");
    }

    private String slugify(String value) {
        String ascii = DIACRITICS.matcher(
                        Normalizer.normalize(value, Normalizer.Form.NFD))
                .replaceAll("")
                .toLowerCase(Locale.ROOT)
                .replace('đ', 'd');
        String slug = NON_SLUG_CHARACTERS.matcher(ascii).replaceAll("-")
                .replaceAll("(^-+|-+$)", "");
        if (slug.isBlank()) {
            slug = "store";
        }
        return slug.substring(0, Math.min(slug.length(), 80));
    }

    private String normalizeName(String value) {
        if (value == null || value.isBlank()) {
            throw badRequest("Store name must not be blank");
        }
        String name = value.trim().replaceAll("\\s+", " ");
        if (name.length() > 160) {
            throw badRequest("Store name must not exceed 160 characters");
        }
        return name;
    }

    private StoreResponse response(Store store, StoreRole role) {
        return new StoreResponse(
                store.getId(),
                store.getName(),
                store.getSlug(),
                store.getStatus(),
                store.isOnboardingCompleted(),
                store.getArchivedAt(),
                role);
    }

    private static ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }

    public record StoreResponse(
            UUID id,
            String name,
            String slug,
            StoreStatus status,
            boolean onboardingCompleted,
            OffsetDateTime archivedAt,
            StoreRole role) {
    }
}
