package vn.omnismart.catalog;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import vn.omnismart.audit.AuditAction;
import vn.omnismart.audit.AuditLogRepository;
import vn.omnismart.identity.AppUser;
import vn.omnismart.identity.AppUserRepository;
import vn.omnismart.identity.IdentityProvisioningService;
import vn.omnismart.membership.MembershipInvitationRepository;
import vn.omnismart.store.Store;
import vn.omnismart.store.StoreMember;
import vn.omnismart.store.StoreMemberRepository;
import vn.omnismart.store.StoreRepository;
import vn.omnismart.store.StoreRole;

@SpringBootTest(properties = {
    "omnismart.product-media.storage-root=target/test-product-media",
    "omnismart.product-media.max-images-per-product=1"
})
@AutoConfigureMockMvc
class ProductMediaTests {

    private static final String SUBJECT = "media-owner";

    @Autowired MockMvc mockMvc;
    @Autowired AppUserRepository userRepository;
    @Autowired StoreRepository storeRepository;
    @Autowired StoreMemberRepository memberRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ProductMediaRepository mediaRepository;
    @Autowired MembershipInvitationRepository invitationRepository;
    @Autowired AuditLogRepository auditLogRepository;

    private UUID storeId;
    private UUID productId;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        invitationRepository.deleteAll();
        mediaRepository.deleteAll();
        productRepository.deleteAll();
        memberRepository.deleteAll();
        storeRepository.deleteAll();
        userRepository.deleteAll();

        AppUser owner = userRepository.save(new AppUser(
                UUID.randomUUID(), "media@test.local", "Media Owner",
                IdentityProvisioningService.GOOGLE_PROVIDER, SUBJECT));
        Store store = storeRepository.save(new Store(UUID.randomUUID(), "Media Store", "media-store"));
        storeId = store.getId();
        memberRepository.save(new StoreMember(storeId, owner.getId(), StoreRole.OWNER));
        Product product = productRepository.saveAndFlush(new Product(
                UUID.randomUUID(), storeId, "IMAGE-1", "Image Product", null,
                new BigDecimal("1.00"), ProductCurrency.VND, 1));
        productId = product.getId();
    }

    @Test
    void uploadsValidatedImageWithServerObjectKeyAndExplicitPrimarySelection() throws Exception {
        byte[] png = validPng();
        MockMultipartFile file = new MockMultipartFile(
                "file", "../../user-name.exe", "application/octet-stream", png);

        mockMvc.perform(multipart("/api/v1/stores/{storeId}/products/{productId}/media", storeId, productId)
                        .file(file).param("primary", "true")
                        .with(login()).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType", is("image/png")))
                .andExpect(jsonPath("$.primary", is(true)))
                .andExpect(jsonPath("$.contentUrl").isNotEmpty());

        ProductMedia media = mediaRepository.findAll().getFirst();
        org.assertj.core.api.Assertions.assertThat(media.getStatus()).isEqualTo(ProductMediaStatus.ATTACHED);
        org.assertj.core.api.Assertions.assertThat(media.getObjectKey())
                .startsWith(storeId + "/" + productId + "/")
                .doesNotContain("user-name", "..", ".exe");

        mockMvc.perform(get("/api/v1/stores/{storeId}/products/{productId}/media", storeId, productId)
                        .with(login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(get(
                            "/api/v1/stores/{storeId}/products/{productId}/media/{mediaId}/content",
                            storeId, productId, media.getId())
                        .with(login()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(png));
        org.assertj.core.api.Assertions.assertThat(auditLogRepository.findByStoreIdOrderByCreatedAtAsc(storeId))
                .singleElement()
                .extracting(log -> log.getAction())
                .isEqualTo(AuditAction.PRODUCT_MEDIA_ATTACHED);
    }

    @Test
    void rejectsSpoofedImageContentAndDoesNotAttachMedia() throws Exception {
        MockMultipartFile fake = new MockMultipartFile(
                "file", "fake.jpg", "image/jpeg", "not-an-image".getBytes());

        mockMvc.perform(multipart("/api/v1/stores/{storeId}/products/{productId}/media", storeId, productId)
                        .file(fake).with(login()).with(csrf()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code", is("PRODUCT_MEDIA_TYPE_UNSUPPORTED")));

        org.assertj.core.api.Assertions.assertThat(mediaRepository.findAll()).isEmpty();
    }

    @Test
    void enforcesConfiguredImageCountLimit() throws Exception {
        MockMultipartFile first = new MockMultipartFile("file", "one.png", "image/png", validPng());
        MockMultipartFile second = new MockMultipartFile("file", "two.png", "image/png", validPng());
        mockMvc.perform(multipart("/api/v1/stores/{storeId}/products/{productId}/media", storeId, productId)
                        .file(first).with(login()).with(csrf()))
                .andExpect(status().isCreated());
        mockMvc.perform(multipart("/api/v1/stores/{storeId}/products/{productId}/media", storeId, productId)
                        .file(second).with(login()).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("PRODUCT_MEDIA_LIMIT_REACHED")));
    }

    @Test
    void ownerCanDeleteWrongImageAndFreeTheUploadSlot() throws Exception {
        MockMultipartFile first = new MockMultipartFile("file", "one.png", "image/png", validPng());
        mockMvc.perform(multipart("/api/v1/stores/{storeId}/products/{productId}/media", storeId, productId)
                        .file(first).with(login()).with(csrf()))
                .andExpect(status().isCreated());
        ProductMedia uploaded = mediaRepository.findAll().getFirst();

        mockMvc.perform(delete(
                            "/api/v1/stores/{storeId}/products/{productId}/media/{mediaId}",
                            storeId, productId, uploaded.getId())
                        .with(login()).with(csrf()))
                .andExpect(status().isNoContent());

        org.assertj.core.api.Assertions.assertThat(mediaRepository.findById(uploaded.getId())).isEmpty();
        mockMvc.perform(get(
                            "/api/v1/stores/{storeId}/products/{productId}/media/{mediaId}/content",
                            storeId, productId, uploaded.getId())
                        .with(login()))
                .andExpect(status().isNotFound());

        MockMultipartFile replacement = new MockMultipartFile(
                "file", "replacement.png", "image/png", validPng());
        mockMvc.perform(multipart("/api/v1/stores/{storeId}/products/{productId}/media", storeId, productId)
                        .file(replacement).with(login()).with(csrf()))
                .andExpect(status().isCreated());
        org.assertj.core.api.Assertions.assertThat(
                        auditLogRepository.findByStoreIdOrderByCreatedAtAsc(storeId))
                .extracting(log -> log.getAction())
                .contains(AuditAction.PRODUCT_MEDIA_DELETED);
    }

    @Test
    void rejectsImageLargerThanConfiguredServiceLimit() throws Exception {
        byte[] oversized = new byte[(5 * 1024 * 1024) + 1];
        MockMultipartFile file = new MockMultipartFile(
                "file", "large.png", "image/png", oversized);

        mockMvc.perform(multipart("/api/v1/stores/{storeId}/products/{productId}/media", storeId, productId)
                        .file(file).with(login()).with(csrf()))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code", is("PRODUCT_MEDIA_TOO_LARGE")));

        org.assertj.core.api.Assertions.assertThat(mediaRepository.findAll()).isEmpty();
    }

    private byte[] validPng() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor
            login() {
        return oidcLogin().idToken(token -> token
                .subject(SUBJECT)
                .claim("email", "media@test.local")
                .claim("email_verified", true));
    }
}
