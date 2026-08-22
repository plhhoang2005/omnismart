package vn.omnismart.store;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import vn.omnismart.identity.AppUser;
import vn.omnismart.identity.AppUserRepository;
import vn.omnismart.identity.IdentityProvisioningService;

@Component("storeAuthorization")
public class StoreAuthorization {

    private final AppUserRepository userRepository;
    private final StoreMemberRepository memberRepository;

    public StoreAuthorization(AppUserRepository userRepository, StoreMemberRepository memberRepository) {
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional(readOnly = true)
    public boolean isMember(Authentication authentication, UUID storeId) {
        return membership(authentication, storeId).isPresent();
    }

    @Transactional(readOnly = true)
    public boolean hasRole(Authentication authentication, UUID storeId, String role) {
        StoreRole requiredRole;
        try {
            requiredRole = StoreRole.valueOf(role);
        } catch (IllegalArgumentException exception) {
            return false;
        }
        return membership(authentication, storeId)
                .map(StoreMember::getRole)
                .filter(requiredRole::equals)
                .isPresent();
    }

    private Optional<StoreMember> membership(Authentication authentication, UUID storeId) {
        if (authentication == null || !(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
            return Optional.empty();
        }

        return userRepository
                .findByProviderAndProviderSubject(
                        IdentityProvisioningService.GOOGLE_PROVIDER,
                        oidcUser.getSubject())
                .map(AppUser::getId)
                .flatMap(userId -> memberRepository.findByStoreIdAndUserId(storeId, userId));
    }
}
