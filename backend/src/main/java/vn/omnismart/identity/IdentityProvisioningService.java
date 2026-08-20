package vn.omnismart.identity;

import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.omnismart.audit.AuditAction;
import vn.omnismart.audit.AuditLogService;
import vn.omnismart.store.Store;
import vn.omnismart.store.StoreMember;
import vn.omnismart.store.StoreMemberRepository;
import vn.omnismart.store.StoreRepository;
import vn.omnismart.store.StoreRole;

@Service
public class IdentityProvisioningService {

    public static final String GOOGLE_PROVIDER = "GOOGLE";

    private final AppUserRepository userRepository;
    private final StoreRepository storeRepository;
    private final StoreMemberRepository memberRepository;
    private final AuditLogService auditLogService;

    public IdentityProvisioningService(
            AppUserRepository userRepository,
            StoreRepository storeRepository,
            StoreMemberRepository memberRepository,
            AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.storeRepository = storeRepository;
        this.memberRepository = memberRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public AppUser provisionGoogleUser(String subject, String email, String displayName) {
        String normalizedEmail = requireText(email, "Google did not provide an email address")
                .toLowerCase(Locale.ROOT);
        String normalizedSubject = requireText(subject, "Google did not provide a subject identifier");
        String normalizedName = displayName == null || displayName.isBlank() ? normalizedEmail : displayName.trim();

        return userRepository.findByProviderAndProviderSubject(GOOGLE_PROVIDER, normalizedSubject)
                .map(user -> updateExistingUser(user, normalizedEmail, normalizedName))
                .orElseGet(() -> createUserAndOwnerStore(normalizedSubject, normalizedEmail, normalizedName));
    }

    private AppUser updateExistingUser(AppUser user, String email, String displayName) {
        userRepository.findByEmailIgnoreCase(email)
                .filter(other -> !other.getId().equals(user.getId()))
                .ifPresent(other -> {
                    throw new IdentityConflictException("The Google email is already linked to another account");
                });
        user.updateProfile(email, displayName);
        return userRepository.save(user);
    }

    private AppUser createUserAndOwnerStore(String subject, String email, String displayName) {
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new IdentityConflictException("The Google email is already linked to another account");
        }

        AppUser user = userRepository.save(
                new AppUser(UUID.randomUUID(), email, displayName, GOOGLE_PROVIDER, subject));
        Store store = storeRepository.save(Store.pendingOnboarding(
                UUID.randomUUID(),
                displayName + "'s Store",
                "store-" + user.getId().toString().substring(0, 12)));
        memberRepository.save(new StoreMember(store.getId(), user.getId(), StoreRole.OWNER));
        auditLogService.record(
                store.getId(), user.getId(), AuditAction.STORE_CREATED,
                "STORE", store.getId(), "onboardingCompleted=false");
        return user;
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IdentityConflictException(message);
        }
        return value.trim();
    }
}
