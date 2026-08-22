package vn.omnismart.store;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import vn.omnismart.store.StoreService.StoreResponse;

@RestController
@RequestMapping("/api/v1/stores")
public class StoreController {

    private final StoreService storeService;

    public StoreController(StoreService storeService) {
        this.storeService = storeService;
    }

    @PostMapping
    ResponseEntity<StoreResponse> create(
            @AuthenticationPrincipal OidcUser principal,
            @Valid @RequestBody CreateStoreRequest request) {
        StoreResponse created = storeService.create(principal, request.name());
        return ResponseEntity.created(URI.create("/api/v1/stores/" + created.id())).body(created);
    }

    @GetMapping
    List<StoreResponse> list(@AuthenticationPrincipal OidcUser principal) {
        return storeService.list(principal);
    }

    @GetMapping("/{storeId}")
    StoreResponse get(
            @AuthenticationPrincipal OidcUser principal,
            @PathVariable UUID storeId) {
        return storeService.get(principal, storeId);
    }

    @PatchMapping("/{storeId}")
    StoreResponse update(
            @AuthenticationPrincipal OidcUser principal,
            @PathVariable UUID storeId,
            @Valid @RequestBody UpdateStoreRequest request) {
        return storeService.update(
                principal,
                storeId,
                request.name(),
                request.status(),
                request.confirmationName());
    }

    record CreateStoreRequest(
            @NotBlank(message = "Store name is required")
            @Size(max = 160, message = "Store name must not exceed 160 characters")
            String name) {
    }

    record UpdateStoreRequest(
            @Size(max = 160, message = "Store name must not exceed 160 characters")
            String name,
            StoreStatus status,
            @Size(max = 160, message = "Confirmation name must not exceed 160 characters")
            String confirmationName) {
    }
}
