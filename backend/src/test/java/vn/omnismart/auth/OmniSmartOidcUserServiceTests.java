package vn.omnismart.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import vn.omnismart.identity.IdentityConflictException;
import vn.omnismart.identity.IdentityProvisioningService;

class OmniSmartOidcUserServiceTests {

    private IdentityProvisioningService provisioningService;
    private OAuth2UserService<OidcUserRequest, OidcUser> delegate;
    private OmniSmartOidcUserService userService;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        provisioningService = mock(IdentityProvisioningService.class);
        delegate = mock(OAuth2UserService.class);
        userService = new OmniSmartOidcUserService(provisioningService, delegate);
    }

    @Test
    void provisionsVerifiedGoogleIdentityWithoutPassingProviderTokens() {
        OidcUserRequest request = mock(OidcUserRequest.class);
        OidcUser user = mock(OidcUser.class);
        when(delegate.loadUser(request)).thenReturn(user);
        when(user.getClaimAsBoolean("email_verified")).thenReturn(true);
        when(user.getSubject()).thenReturn("google-subject-1");
        when(user.getClaimAsString("email")).thenReturn("owner@example.com");
        when(user.getClaimAsString("name")).thenReturn("Store Owner");

        OidcUser result = userService.loadUser(request);

        assertThat(result).isSameAs(user);
        verify(provisioningService).provisionGoogleUser(
                "google-subject-1", "owner@example.com", "Store Owner");
    }

    @Test
    void rejectsUnverifiedGoogleEmail() {
        OidcUserRequest request = mock(OidcUserRequest.class);
        OidcUser user = mock(OidcUser.class);
        when(delegate.loadUser(request)).thenReturn(user);
        when(user.getClaimAsBoolean("email_verified")).thenReturn(false);

        assertThatThrownBy(() -> userService.loadUser(request))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("Google email must be verified");
        verify(provisioningService, never()).provisionGoogleUser(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void convertsIdentityConflictIntoRejectedLogin() {
        OidcUserRequest request = mock(OidcUserRequest.class);
        OidcUser user = mock(OidcUser.class);
        when(delegate.loadUser(request)).thenReturn(user);
        when(user.getClaimAsBoolean("email_verified")).thenReturn(true);
        when(user.getSubject()).thenReturn("google-subject-2");
        when(user.getClaimAsString("email")).thenReturn("conflict@example.com");
        when(user.getClaimAsString("name")).thenReturn("Conflicting User");
        when(provisioningService.provisionGoogleUser(
                "google-subject-2", "conflict@example.com", "Conflicting User"))
                .thenThrow(new IdentityConflictException("already linked"));

        assertThatThrownBy(() -> userService.loadUser(request))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("already linked");
    }
}
