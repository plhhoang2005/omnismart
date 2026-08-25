package vn.omnismart.auth;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import vn.omnismart.identity.CurrentUserService;
import vn.omnismart.identity.CurrentUserService.CurrentUserResponse;
import vn.omnismart.identity.IdentityProvisioningService;

/**
 * Creates a server-managed test session without contacting Google.
 *
 * <p>The controller is absent unless both the local profile and the explicit
 * opt-in property are enabled. Staging and production therefore cannot expose
 * this authentication path.</p>
 */
@RestController
@Profile("local")
@ConditionalOnProperty(name = "omnismart.security.dev-login.enabled", havingValue = "true")
@RequestMapping("/api/v1/auth")
public class LocalDevLoginController {

    private static final String REGISTRATION_ID = "google";
    private static final String SUBJECT_PREFIX = "local-dev:";

    private final IdentityProvisioningService provisioningService;
    private final CurrentUserService currentUserService;
    private final HttpSessionSecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    public LocalDevLoginController(
            IdentityProvisioningService provisioningService,
            CurrentUserService currentUserService) {
        this.provisioningService = provisioningService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/dev-login")
    CurrentUserResponse login(
            @Valid @RequestBody DevLoginRequest loginRequest,
            HttpServletRequest request,
            HttpServletResponse response) {
        String email = loginRequest.email().trim().toLowerCase(Locale.ROOT);
        String displayName = loginRequest.displayName().trim();
        String subject = SUBJECT_PREFIX + UUID.nameUUIDFromBytes(email.getBytes(StandardCharsets.UTF_8));

        provisioningService.provisionGoogleUser(subject, email, displayName);

        OidcUser principal = localPrincipal(subject, email, displayName);
        OAuth2AuthenticationToken authentication = new OAuth2AuthenticationToken(
                principal, principal.getAuthorities(), REGISTRATION_ID);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        return currentUserService.getCurrentUser(subject);
    }

    private OidcUser localPrincipal(String subject, String email, String displayName) {
        Instant issuedAt = Instant.now();
        OidcIdToken idToken = new OidcIdToken(
                "local-dev-token",
                issuedAt,
                issuedAt.plusSeconds(8 * 60 * 60),
                Map.of(
                        "sub", subject,
                        "email", email,
                        "email_verified", true,
                        "name", displayName,
                        "iss", "https://local.omnismart.test"));
        return new DefaultOidcUser(
                List.of(new SimpleGrantedAuthority("ROLE_USER")), idToken, "sub");
    }

    record DevLoginRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(max = 200) String displayName) {
    }
}
