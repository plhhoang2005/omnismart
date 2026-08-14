package vn.omnismart.store;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import vn.omnismart.identity.AppUser;
import vn.omnismart.identity.AppUserRepository;
import vn.omnismart.identity.IdentityProvisioningService;

@RestController
@RequestMapping("/api/v1/stores")
public class StoreAccessController {

    private final AppUserRepository userRepository;
    private final StoreMemberRepository memberRepository;

    public StoreAccessController(
            AppUserRepository userRepository,
            StoreMemberRepository memberRepository) {
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
    }

    @GetMapping("/{storeId}/membership")
    @PreAuthorize("@storeAuthorization.isMember(authentication, #storeId)")
    StoreAccessResponse membership(
            @PathVariable UUID storeId,
            @AuthenticationPrincipal OidcUser principal) {
        return response(storeId, principal);
    }

    @GetMapping("/{storeId}/owner-access")
    @PreAuthorize("@storeAuthorization.hasRole(authentication, #storeId, 'OWNER')")
    StoreAccessResponse ownerAccess(
            @PathVariable UUID storeId,
            @AuthenticationPrincipal OidcUser principal) {
        return response(storeId, principal);
    }

    private StoreAccessResponse response(UUID storeId, OidcUser principal) {
        AppUser user = userRepository
                .findByProviderAndProviderSubject(
                        IdentityProvisioningService.GOOGLE_PROVIDER,
                        principal.getSubject())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        StoreMember membership = memberRepository.findByStoreIdAndUserId(storeId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
        return new StoreAccessResponse(storeId, membership.getRole().name());
    }

    record StoreAccessResponse(UUID storeId, String role) {
    }
}
