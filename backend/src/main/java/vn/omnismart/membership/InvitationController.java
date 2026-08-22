package vn.omnismart.membership;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import vn.omnismart.membership.InvitationService.CreatedInvitationResponse;
import vn.omnismart.membership.InvitationService.InvitationResponse;
import vn.omnismart.store.StoreRole;

@RestController
@RequestMapping("/api/v1")
public class InvitationController {

    private final InvitationService invitationService;

    public InvitationController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @PostMapping("/stores/{storeId}/invitations")
    ResponseEntity<CreatedInvitationResponse> create(
            @AuthenticationPrincipal OidcUser principal,
            @PathVariable UUID storeId,
            @Valid @RequestBody CreateInvitationRequest request) {
        CreatedInvitationResponse created = invitationService.create(
                principal,
                storeId,
                request.email(),
                request.role(),
                request.confirmationName());
        return ResponseEntity.created(
                        URI.create("/api/v1/invitations/" + created.id()))
                .body(created);
    }

    @GetMapping("/stores/{storeId}/invitations")
    List<InvitationResponse> listForStore(
            @AuthenticationPrincipal OidcUser principal,
            @PathVariable UUID storeId) {
        return invitationService.listForStore(principal, storeId);
    }

    @GetMapping("/invitations")
    List<InvitationResponse> listForCurrentUser(
            @AuthenticationPrincipal OidcUser principal) {
        return invitationService.listForCurrentUser(principal);
    }

    @PostMapping("/invitations/{invitationId}/accept")
    InvitationResponse accept(
            @AuthenticationPrincipal OidcUser principal,
            @PathVariable UUID invitationId,
            @Valid @RequestBody InvitationTokenRequest request) {
        return invitationService.accept(principal, invitationId, request.token());
    }

    @PostMapping("/invitations/{invitationId}/decline")
    InvitationResponse decline(
            @AuthenticationPrincipal OidcUser principal,
            @PathVariable UUID invitationId,
            @Valid @RequestBody InvitationTokenRequest request) {
        return invitationService.decline(principal, invitationId, request.token());
    }

    @DeleteMapping("/stores/{storeId}/invitations/{invitationId}")
    ResponseEntity<Void> revoke(
            @AuthenticationPrincipal OidcUser principal,
            @PathVariable UUID storeId,
            @PathVariable UUID invitationId,
            @Valid @RequestBody RevokeInvitationRequest request) {
        invitationService.revoke(principal, storeId, invitationId, request.confirmationEmail());
        return ResponseEntity.noContent().build();
    }

    record CreateInvitationRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Email must be valid")
            @Size(max = 320)
            String email,
            @NotNull(message = "Role is required") StoreRole role,
            @Size(max = 160) String confirmationName) {

        @Override
        public String toString() {
            return "CreateInvitationRequest[email=[REDACTED], role=" + role
                    + ", confirmationName=[REDACTED]]";
        }
    }

    record InvitationTokenRequest(
            @NotBlank(message = "Invitation token is required")
            @Size(max = 200)
            String token) {

        @Override
        public String toString() {
            return "InvitationTokenRequest[token=[REDACTED]]";
        }
    }

    record RevokeInvitationRequest(
            @NotBlank(message = "Invitation email confirmation is required")
            @Email(message = "Invitation email must be valid")
            @Size(max = 320)
            String confirmationEmail) {

        @Override
        public String toString() {
            return "RevokeInvitationRequest[confirmationEmail=[REDACTED]]";
        }
    }
}
