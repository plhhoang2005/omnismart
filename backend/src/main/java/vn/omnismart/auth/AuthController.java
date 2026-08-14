package vn.omnismart.auth;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import vn.omnismart.identity.CurrentUserService;
import vn.omnismart.identity.CurrentUserService.CurrentUserResponse;

@RestController
@RequestMapping("/api/v1")
public class AuthController {

    private final CurrentUserService currentUserService;

    public AuthController(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    @GetMapping("/auth/csrf")
    CsrfResponse csrf(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (token == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "CSRF token unavailable");
        }
        return new CsrfResponse(token.getHeaderName(), token.getParameterName(), token.getToken());
    }

    @GetMapping("/me")
    CurrentUserResponse me(@AuthenticationPrincipal OidcUser principal) {
        return currentUserService.getCurrentUser(principal.getSubject());
    }

    record CsrfResponse(String headerName, String parameterName, String token) {
    }
}
