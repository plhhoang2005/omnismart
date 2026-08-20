package vn.omnismart.store;

import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import vn.omnismart.identity.AppUser;
import vn.omnismart.identity.AppUserRepository;
import vn.omnismart.identity.IdentityProvisioningService;

@Component("storeAuthorization")
public class StoreAuthorizationService {

    private final AppUserRepository userRepository;
    private final StoreMemberRepository memberRepository;

    public StoreAuthorizationService(
            AppUserRepository userRepository,
            StoreMemberRepository memberRepository) {
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional(readOnly = true)
    public AppUser requireUser(OidcUser principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return userRepository
                .findByProviderAndProviderSubject(
                        IdentityProvisioningService.GOOGLE_PROVIDER,
                        principal.getSubject())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    @Transactional(readOnly = true)
    public StoreMember requireMembership(OidcUser principal, UUID storeId) {
        AppUser user = requireUser(principal);
        return memberRepository.findByStoreIdAndUserId(storeId, user.getId())
                .orElseThrow(StoreAuthorizationService::notFound);
    }

    @Transactional(readOnly = true)
    public StoreMember requireOwner(OidcUser principal, UUID storeId) {
        StoreMember membership = requireMembership(principal, storeId);
        if (membership.getRole() != StoreRole.OWNER) {
            throw notFound();
        }
        return membership;
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

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Store not found");
    }
}
