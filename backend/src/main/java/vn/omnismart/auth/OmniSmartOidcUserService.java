package vn.omnismart.auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import vn.omnismart.identity.IdentityConflictException;
import vn.omnismart.identity.IdentityProvisioningService;

@Service
public class OmniSmartOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final IdentityProvisioningService provisioningService;
    private final OAuth2UserService<OidcUserRequest, OidcUser> delegate;

    @Autowired
    public OmniSmartOidcUserService(IdentityProvisioningService provisioningService) {
        this(provisioningService, new OidcUserService());
    }

    OmniSmartOidcUserService(
            IdentityProvisioningService provisioningService,
            OAuth2UserService<OidcUserRequest, OidcUser> delegate) {
        this.provisioningService = provisioningService;
        this.delegate = delegate;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser user = delegate.loadUser(userRequest);
        if (!Boolean.TRUE.equals(user.getClaimAsBoolean("email_verified"))) {
            throw authenticationFailure("email_not_verified", "Google email must be verified");
        }

        try {
            provisioningService.provisionGoogleUser(
                    user.getSubject(),
                    user.getClaimAsString("email"),
                    user.getClaimAsString("name"));
            return user;
        } catch (IdentityConflictException exception) {
            throw authenticationFailure("identity_conflict", exception.getMessage());
        }
    }

    private OAuth2AuthenticationException authenticationFailure(String code, String message) {
        return new OAuth2AuthenticationException(new OAuth2Error(code), message);
    }
}
