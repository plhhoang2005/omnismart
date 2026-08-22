package vn.omnismart.catalog;

import java.util.List;
import java.util.UUID;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import vn.omnismart.catalog.ProductMediaService.MediaContent;
import vn.omnismart.catalog.ProductMediaService.MediaResponse;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/products/{productId}/media")
public class ProductMediaController {

    private final ProductMediaService mediaService;

    public ProductMediaController(ProductMediaService mediaService) {
        this.mediaService = mediaService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<MediaResponse> upload(
            @AuthenticationPrincipal OidcUser principal,
            @PathVariable UUID storeId,
            @PathVariable UUID productId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean primary) {
        MediaResponse uploaded = mediaService.upload(principal, storeId, productId, file, primary);
        return ResponseEntity.status(201).body(uploaded);
    }

    @GetMapping
    List<MediaResponse> list(
            @AuthenticationPrincipal OidcUser principal,
            @PathVariable UUID storeId,
            @PathVariable UUID productId) {
        return mediaService.list(principal, storeId, productId);
    }

    @PatchMapping("/{mediaId}/primary")
    MediaResponse selectPrimary(
            @AuthenticationPrincipal OidcUser principal,
            @PathVariable UUID storeId,
            @PathVariable UUID productId,
            @PathVariable UUID mediaId) {
        return mediaService.selectPrimary(principal, storeId, productId, mediaId);
    }

    @DeleteMapping("/{mediaId}")
    ResponseEntity<Void> delete(
            @AuthenticationPrincipal OidcUser principal,
            @PathVariable UUID storeId,
            @PathVariable UUID productId,
            @PathVariable UUID mediaId) {
        mediaService.delete(principal, storeId, productId, mediaId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{mediaId}/content")
    ResponseEntity<InputStreamResource> content(
            @AuthenticationPrincipal OidcUser principal,
            @PathVariable UUID storeId,
            @PathVariable UUID productId,
            @PathVariable UUID mediaId) {
        MediaContent content = mediaService.openContent(principal, storeId, productId, mediaId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .contentLength(content.byteSize())
                .cacheControl(CacheControl.noStore())
                .body(new InputStreamResource(content.input()));
    }
}
