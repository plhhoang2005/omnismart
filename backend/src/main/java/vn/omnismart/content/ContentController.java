package vn.omnismart.content;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import vn.omnismart.content.ContentWorkflowService.ApprovalResponse;
import vn.omnismart.content.ContentWorkflowService.ContentPageResponse;
import vn.omnismart.content.ContentWorkflowService.ContentResponse;
import vn.omnismart.content.ContentWorkflowService.ContentVersionResponse;

@Validated
@RestController
@RequestMapping("/api/v1/stores/{storeId}")
public class ContentController {

    private final ContentWorkflowService workflowService;

    public ContentController(ContentWorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @PostMapping("/products/{productId}/contents")
    ResponseEntity<ContentResponse> create(
            @AuthenticationPrincipal OidcUser principal,
            @PathVariable UUID storeId,
            @PathVariable UUID productId,
            @Valid @RequestBody CreateContentRequest request) {
        ContentResponse created = workflowService.create(
                principal, storeId, productId, request.channel(), request.body());
        return ResponseEntity.created(
                        URI.create("/api/v1/stores/" + storeId + "/contents/" + created.id()))
                .body(created);
    }

    @GetMapping("/contents")
    ContentPageResponse list(
            @AuthenticationPrincipal OidcUser principal,
            @PathVariable UUID storeId,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) ContentStatus status,
            @RequestParam(required = false) ContentChannel channel,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return workflowService.list(principal, storeId, productId, status, channel, page, size);
    }

    @GetMapping("/contents/{contentId}")
    ContentResponse get(
            @AuthenticationPrincipal OidcUser principal,
            @PathVariable UUID storeId,
            @PathVariable UUID contentId) {
        return workflowService.get(principal, storeId, contentId);
    }

    @PatchMapping("/contents/{contentId}")
    ContentResponse edit(
            @AuthenticationPrincipal OidcUser principal,
            @PathVariable UUID storeId,
            @PathVariable UUID contentId,
            @Valid @RequestBody EditContentRequest request) {
        return workflowService.edit(
                principal, storeId, contentId, request.version(), request.body());
    }

    @GetMapping("/contents/{contentId}/versions")
    List<ContentVersionResponse> versions(
            @AuthenticationPrincipal OidcUser principal,
            @PathVariable UUID storeId,
            @PathVariable UUID contentId) {
        return workflowService.versions(principal, storeId, contentId);
    }

    @GetMapping("/contents/{contentId}/approvals")
    List<ApprovalResponse> approvals(
            @AuthenticationPrincipal OidcUser principal,
            @PathVariable UUID storeId,
            @PathVariable UUID contentId) {
        return workflowService.approvals(principal, storeId, contentId);
    }

    @PostMapping("/contents/{contentId}/submit")
    ContentResponse submit(
            @AuthenticationPrincipal OidcUser principal,
            @PathVariable UUID storeId,
            @PathVariable UUID contentId,
            @Valid @RequestBody VersionRequest request) {
        return workflowService.submit(principal, storeId, contentId, request.version());
    }

    @PostMapping("/contents/{contentId}/approve")
    ContentResponse approve(
            @AuthenticationPrincipal OidcUser principal,
            @PathVariable UUID storeId,
            @PathVariable UUID contentId,
            @Valid @RequestBody VersionRequest request) {
        return workflowService.approve(principal, storeId, contentId, request.version());
    }

    @PostMapping("/contents/{contentId}/reject")
    ContentResponse reject(
            @AuthenticationPrincipal OidcUser principal,
            @PathVariable UUID storeId,
            @PathVariable UUID contentId,
            @Valid @RequestBody RejectContentRequest request) {
        return workflowService.reject(
                principal, storeId, contentId, request.version(), request.reason());
    }

    record CreateContentRequest(
            @NotNull ContentChannel channel,
            @NotBlank @Size(max = 10000) String body) {

        @Override
        public String toString() {
            return "CreateContentRequest[channel=" + channel + ", body=[REDACTED]]";
        }
    }

    record EditContentRequest(
            @NotNull @PositiveOrZero Long version,
            @NotBlank @Size(max = 10000) String body) {

        @Override
        public String toString() {
            return "EditContentRequest[version=" + version + ", body=[REDACTED]]";
        }
    }

    record VersionRequest(@NotNull @PositiveOrZero Long version) {
    }

    record RejectContentRequest(
            @NotNull @PositiveOrZero Long version,
            @NotBlank @Size(max = 1000) String reason) {

        @Override
        public String toString() {
            return "RejectContentRequest[version=" + version + ", reason=[REDACTED]]";
        }
    }
}
