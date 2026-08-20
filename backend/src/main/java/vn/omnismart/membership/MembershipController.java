package vn.omnismart.membership;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import vn.omnismart.membership.MembershipService.MemberResponse;
import vn.omnismart.store.StoreRole;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/members")
public class MembershipController {

    private final MembershipService membershipService;

    public MembershipController(MembershipService membershipService) {
        this.membershipService = membershipService;
    }

    @GetMapping
    List<MemberResponse> list(
            @AuthenticationPrincipal OidcUser principal,
            @PathVariable UUID storeId) {
        return membershipService.list(principal, storeId);
    }

    @PatchMapping("/{userId}")
    MemberResponse changeRole(
            @AuthenticationPrincipal OidcUser principal,
            @PathVariable UUID storeId,
            @PathVariable UUID userId,
            @Valid @RequestBody ChangeRoleRequest request) {
        return membershipService.changeRole(
                principal,
                storeId,
                userId,
                request.role(),
                request.confirmationName());
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(
            @AuthenticationPrincipal OidcUser principal,
            @PathVariable UUID storeId,
            @PathVariable UUID userId,
            @Valid @RequestBody RevokeMemberRequest request) {
        membershipService.revoke(principal, storeId, userId, request.confirmationName());
    }

    record ChangeRoleRequest(
            @NotNull(message = "Role is required") StoreRole role,
            @NotBlank(message = "Store name confirmation is required")
            @Size(max = 160) String confirmationName) {
    }

    record RevokeMemberRequest(
            @NotBlank(message = "Store name confirmation is required")
            @Size(max = 160) String confirmationName) {
    }
}
