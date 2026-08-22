package vn.omnismart.identity;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import vn.omnismart.store.Store;
import vn.omnismart.store.StoreMember;
import vn.omnismart.store.StoreMemberRepository;
import vn.omnismart.store.StoreRepository;

@Service
public class CurrentUserService {

    private final AppUserRepository userRepository;
    private final StoreMemberRepository memberRepository;
    private final StoreRepository storeRepository;

    public CurrentUserService(
            AppUserRepository userRepository,
            StoreMemberRepository memberRepository,
            StoreRepository storeRepository) {
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
        this.storeRepository = storeRepository;
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser(String providerSubject) {
        AppUser user = userRepository
                .findByProviderAndProviderSubject(IdentityProvisioningService.GOOGLE_PROVIDER, providerSubject)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User is not provisioned"));

        List<MembershipResponse> memberships = memberRepository.findByUserId(user.getId()).stream()
                .map(this::toMembership)
                .sorted(Comparator.comparing(MembershipResponse::storeName))
                .toList();

        return new CurrentUserResponse(user.getId(), user.getEmail(), user.getDisplayName(), memberships);
    }

    private MembershipResponse toMembership(StoreMember membership) {
        Store store = storeRepository.findById(membership.getStoreId())
                .orElseThrow(() -> new IllegalStateException("Membership references a missing store"));
        return new MembershipResponse(
                store.getId(),
                store.getName(),
                store.getSlug(),
                membership.getRole().name(),
                store.getStatus().name(),
                store.isOnboardingCompleted());
    }

    public record CurrentUserResponse(
            UUID id,
            String email,
            String displayName,
            List<MembershipResponse> memberships) {

        @Override
        public String toString() {
            return "CurrentUserResponse[id=" + id
                    + ", email=[REDACTED]"
                    + ", displayName=[REDACTED]"
                    + ", memberships=" + memberships + "]";
        }
    }

    public record MembershipResponse(
            UUID storeId,
            String storeName,
            String storeSlug,
            String role,
            String status,
            boolean onboardingCompleted) {
    }
}
