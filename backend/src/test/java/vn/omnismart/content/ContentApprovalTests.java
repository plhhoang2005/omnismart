package vn.omnismart.content;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import vn.omnismart.audit.AuditAction;
import vn.omnismart.audit.AuditLogRepository;
import vn.omnismart.catalog.Product;
import vn.omnismart.catalog.ProductCurrency;
import vn.omnismart.catalog.ProductMediaRepository;
import vn.omnismart.catalog.ProductRepository;
import vn.omnismart.identity.AppUser;
import vn.omnismart.identity.AppUserRepository;
import vn.omnismart.identity.IdentityProvisioningService;
import vn.omnismart.membership.MembershipInvitationRepository;
import vn.omnismart.store.Store;
import vn.omnismart.store.StoreMember;
import vn.omnismart.store.StoreMemberRepository;
import vn.omnismart.store.StoreRepository;
import vn.omnismart.store.StoreRole;

@SpringBootTest
@AutoConfigureMockMvc
class ContentApprovalTests {

    private static final String OWNER_SUBJECT = "content-owner";
    private static final String STAFF_SUBJECT = "content-staff";
    private static final String OTHER_SUBJECT = "content-other";

    @Autowired MockMvc mockMvc;
    @Autowired ContentItemRepository itemRepository;
    @Autowired ContentVersionRepository versionRepository;
    @Autowired ContentApprovalRepository approvalRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ProductMediaRepository mediaRepository;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired MembershipInvitationRepository invitationRepository;
    @Autowired StoreMemberRepository memberRepository;
    @Autowired StoreRepository storeRepository;
    @Autowired AppUserRepository userRepository;

    private UUID storeId;
    private UUID otherStoreId;
    private Product product;

    @BeforeEach
    void setUp() {
        approvalRepository.deleteAll();
        versionRepository.deleteAll();
        itemRepository.deleteAll();
        auditLogRepository.deleteAll();
        invitationRepository.deleteAll();
        mediaRepository.deleteAll();
        productRepository.deleteAll();
        memberRepository.deleteAll();
        storeRepository.deleteAll();
        userRepository.deleteAll();

        AppUser owner = userRepository.save(user(OWNER_SUBJECT));
        AppUser staff = userRepository.save(user(STAFF_SUBJECT));
        AppUser other = userRepository.save(user(OTHER_SUBJECT));
        Store store = storeRepository.save(new Store(
                UUID.randomUUID(), "Content Store", "content-store"));
        Store otherStore = storeRepository.save(new Store(
                UUID.randomUUID(), "Other Store", "other-content-store"));
        storeId = store.getId();
        otherStoreId = otherStore.getId();
        memberRepository.save(new StoreMember(storeId, owner.getId(), StoreRole.OWNER));
        memberRepository.save(new StoreMember(storeId, staff.getId(), StoreRole.STAFF));
        memberRepository.save(new StoreMember(otherStoreId, other.getId(), StoreRole.OWNER));
        product = productRepository.saveAndFlush(new Product(
                UUID.randomUUID(),
                storeId,
                "CONTENT-1",
                "Content product",
                null,
                new BigDecimal("100.00"),
                ProductCurrency.VND,
                5));
    }

    @Test
    void staffCreatesEditsListsAndReadsImmutableVersions() throws Exception {
        UUID contentId = createDraft(STAFF_SUBJECT, "FACEBOOK", "  First draft  ");

        mockMvc.perform(patch("/api/v1/stores/{storeId}/contents/{contentId}", storeId, contentId)
                        .with(login(STAFF_SUBJECT)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"body\":\"Second draft\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("DRAFT")))
                .andExpect(jsonPath("$.version", is(1)))
                .andExpect(jsonPath("$.currentVersionNumber", is(2)))
                .andExpect(jsonPath("$.currentVersion.body", is("Second draft")));

        mockMvc.perform(get("/api/v1/stores/{storeId}/contents/{contentId}/versions", storeId, contentId)
                        .with(login(STAFF_SUBJECT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].versionNumber", is(2)))
                .andExpect(jsonPath("$[0].body", is("Second draft")))
                .andExpect(jsonPath("$[1].body", is("First draft")));

        mockMvc.perform(get("/api/v1/stores/{storeId}/contents", storeId)
                        .with(login(STAFF_SUBJECT))
                        .param("productId", product.getId().toString())
                        .param("status", "DRAFT")
                        .param("channel", "FACEBOOK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id", is(contentId.toString())));
        mockMvc.perform(get("/api/v1/stores/{storeId}/contents", storeId)
                        .with(login(STAFF_SUBJECT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));

        org.assertj.core.api.Assertions.assertThat(
                        auditLogRepository.findByStoreIdOrderByCreatedAtAsc(storeId))
                .extracting(log -> log.getAction())
                .containsExactly(
                        AuditAction.CONTENT_CREATED,
                        AuditAction.CONTENT_VERSION_CREATED,
                        AuditAction.CONTENT_VERSION_CREATED);
    }

    @Test
    void staffSubmitsAndOwnerApprovesExactVersion() throws Exception {
        UUID contentId = createDraft(STAFF_SUBJECT, "TIKTOK", "Video script");

        mockMvc.perform(post("/api/v1/stores/{storeId}/contents/{contentId}/submit", storeId, contentId)
                        .with(login(STAFF_SUBJECT)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("IN_REVIEW")))
                .andExpect(jsonPath("$.version", is(1)));

        mockMvc.perform(post("/api/v1/stores/{storeId}/contents/{contentId}/approve", storeId, contentId)
                        .with(login(OWNER_SUBJECT)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("APPROVED")))
                .andExpect(jsonPath("$.version", is(2)));

        mockMvc.perform(get("/api/v1/stores/{storeId}/contents/{contentId}/approvals", storeId, contentId)
                        .with(login(STAFF_SUBJECT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status", is("APPROVED")))
                .andExpect(jsonPath("$[0].reviewedByUserId").isNotEmpty())
                .andExpect(jsonPath("$[0].rejectionReason").doesNotExist());

        org.assertj.core.api.Assertions.assertThat(
                        auditLogRepository.findByStoreIdOrderByCreatedAtAsc(storeId))
                .extracting(log -> log.getAction())
                .contains(
                        AuditAction.CONTENT_SUBMITTED,
                        AuditAction.CONTENT_APPROVED);
    }

    @Test
    void rejectedContentMustBeChangedBeforeItCanBeResubmitted() throws Exception {
        UUID contentId = createDraft(STAFF_SUBJECT, "MARKETPLACE", "Wrong price");
        submit(contentId, STAFF_SUBJECT, 0);

        mockMvc.perform(post("/api/v1/stores/{storeId}/contents/{contentId}/reject", storeId, contentId)
                        .with(login(OWNER_SUBJECT)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":1,\"reason\":\"Commercial facts need correction\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("REJECTED")))
                .andExpect(jsonPath("$.version", is(2)));

        mockMvc.perform(post("/api/v1/stores/{storeId}/contents/{contentId}/submit", storeId, contentId)
                        .with(login(STAFF_SUBJECT)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":2}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("CONTENT_INVALID_TRANSITION")));

        mockMvc.perform(patch("/api/v1/stores/{storeId}/contents/{contentId}", storeId, contentId)
                        .with(login(STAFF_SUBJECT)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":2,\"body\":\"Wrong price\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("CONTENT_UPDATE_UNCHANGED")));

        mockMvc.perform(patch("/api/v1/stores/{storeId}/contents/{contentId}", storeId, contentId)
                        .with(login(STAFF_SUBJECT)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":2,\"body\":\"Corrected price\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("DRAFT")))
                .andExpect(jsonPath("$.currentVersionNumber", is(2)))
                .andExpect(jsonPath("$.version", is(3)));

        submit(contentId, STAFF_SUBJECT, 3);
        org.assertj.core.api.Assertions.assertThat(
                        approvalRepository.findByContentItemIdAndStoreIdOrderBySubmittedAtDesc(
                                contentId, storeId))
                .hasSize(2);
        org.assertj.core.api.Assertions.assertThat(
                        auditLogRepository.findByStoreIdOrderByCreatedAtAsc(storeId))
                .allSatisfy(log -> org.assertj.core.api.Assertions.assertThat(log.getDetails())
                        .doesNotContain("Wrong price", "Commercial facts need correction"));
    }

    @Test
    void staffCannotReviewAndApprovedContentIsTerminal() throws Exception {
        UUID contentId = createDraft(STAFF_SUBJECT, "FACEBOOK", "Review me");
        submit(contentId, STAFF_SUBJECT, 0);

        mockMvc.perform(post("/api/v1/stores/{storeId}/contents/{contentId}/approve", storeId, contentId)
                        .with(login(STAFF_SUBJECT)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":1}"))
                .andExpect(status().isNotFound());

        approve(contentId, OWNER_SUBJECT, 1);
        mockMvc.perform(patch("/api/v1/stores/{storeId}/contents/{contentId}", storeId, contentId)
                        .with(login(STAFF_SUBJECT)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":2,\"body\":\"Change approved\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("CONTENT_INVALID_TRANSITION")));
    }

    @Test
    void staleAndRepeatedRequestsReturnStableConflicts() throws Exception {
        UUID contentId = createDraft(OWNER_SUBJECT, "FACEBOOK", "Lock me");

        mockMvc.perform(patch("/api/v1/stores/{storeId}/contents/{contentId}", storeId, contentId)
                        .with(login(OWNER_SUBJECT)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"body\":\"Changed\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/stores/{storeId}/contents/{contentId}", storeId, contentId)
                        .with(login(OWNER_SUBJECT)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"body\":\"Stale\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("CONTENT_VERSION_CONFLICT")));

        submit(contentId, OWNER_SUBJECT, 1);
        mockMvc.perform(post("/api/v1/stores/{storeId}/contents/{contentId}/submit", storeId, contentId)
                        .with(login(OWNER_SUBJECT)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":2}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("CONTENT_INVALID_TRANSITION")));
    }

    @Test
    void ownerCanApproveTheirOwnDraft() throws Exception {
        UUID contentId = createDraft(OWNER_SUBJECT, "FACEBOOK", "Owner-authored draft");
        submit(contentId, OWNER_SUBJECT, 0);
        approve(contentId, OWNER_SUBJECT, 1);

        org.assertj.core.api.Assertions.assertThat(itemRepository.findById(contentId))
                .get()
                .extracting(ContentItem::getStatus)
                .isEqualTo(ContentStatus.APPROVED);
    }

    @Test
    void crossTenantResourcesAndOwnerActionsRemainHidden() throws Exception {
        UUID contentId = createDraft(OWNER_SUBJECT, "FACEBOOK", "Private content");

        mockMvc.perform(get("/api/v1/stores/{storeId}/contents/{contentId}", storeId, contentId)
                        .with(login(OTHER_SUBJECT)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/stores/{storeId}/contents/{contentId}", otherStoreId, contentId)
                        .with(login(OTHER_SUBJECT)))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/v1/stores/{storeId}/contents/{contentId}", otherStoreId, contentId)
                        .with(login(OTHER_SUBJECT)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"body\":\"Stolen\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void validationAuthenticationAndProductLifecycleAreEnforced() throws Exception {
        mockMvc.perform(post("/api/v1/stores/{storeId}/products/{productId}/contents", storeId, product.getId())
                        .with(login(STAFF_SUBJECT)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"FACEBOOK\",\"body\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));

        mockMvc.perform(post("/api/v1/stores/{storeId}/products/{productId}/contents", storeId, product.getId())
                        .with(login(STAFF_SUBJECT)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"FACEBOOK\",\"body\":\"Draft\",\"unexpected\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("REQUEST_BODY_INVALID")));

        mockMvc.perform(post("/api/v1/stores/{storeId}/products/{productId}/contents", storeId, product.getId())
                        .with(login(STAFF_SUBJECT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"FACEBOOK\",\"body\":\"No CSRF\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/stores/{storeId}/contents", storeId))
                .andExpect(status().isUnauthorized());

        product.archive();
        productRepository.saveAndFlush(product);
        mockMvc.perform(post("/api/v1/stores/{storeId}/products/{productId}/contents", storeId, product.getId())
                        .with(login(STAFF_SUBJECT)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"FACEBOOK\",\"body\":\"Archived product\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("CONTENT_PRODUCT_INACTIVE")));
    }

    @Test
    void pendingOnboardingStoreCannotCreateContent() throws Exception {
        Store pendingStore = storeRepository.save(Store.pendingOnboarding(
                UUID.randomUUID(), "Pending Content Store", "pending-content-store"));
        AppUser owner = userRepository.findByProviderAndProviderSubject(
                        IdentityProvisioningService.GOOGLE_PROVIDER, OWNER_SUBJECT)
                .orElseThrow();
        memberRepository.save(new StoreMember(pendingStore.getId(), owner.getId(), StoreRole.OWNER));
        Product pendingProduct = productRepository.saveAndFlush(new Product(
                UUID.randomUUID(), pendingStore.getId(), "PENDING-1", "Pending product", null,
                new BigDecimal("1.00"), ProductCurrency.VND, 1));

        mockMvc.perform(post(
                        "/api/v1/stores/{storeId}/products/{productId}/contents",
                        pendingStore.getId(),
                        pendingProduct.getId())
                        .with(login(OWNER_SUBJECT)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"FACEBOOK\",\"body\":\"Pending\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("STORE_ONBOARDING_REQUIRED")));
    }

    @Test
    void ownerCanRejectPendingReviewAfterProductIsArchived() throws Exception {
        UUID contentId = createDraft(STAFF_SUBJECT, "FACEBOOK", "Pending archive");
        submit(contentId, STAFF_SUBJECT, 0);
        product.archive();
        productRepository.saveAndFlush(product);

        mockMvc.perform(post("/api/v1/stores/{storeId}/contents/{contentId}/approve", storeId, contentId)
                        .with(login(OWNER_SUBJECT)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("CONTENT_PRODUCT_INACTIVE")));
        mockMvc.perform(post("/api/v1/stores/{storeId}/contents/{contentId}/reject", storeId, contentId)
                        .with(login(OWNER_SUBJECT)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":1,\"reason\":\"Product was archived\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("REJECTED")));
    }

    @Test
    void requestAndResponseDiagnosticsRedactContentText() {
        String sensitiveBody = "private campaign text";
        String sensitiveReason = "private rejection reason";
        ContentController.CreateContentRequest request =
                new ContentController.CreateContentRequest(ContentChannel.FACEBOOK, sensitiveBody);
        ContentWorkflowService.ContentVersionResponse version =
                new ContentWorkflowService.ContentVersionResponse(
                        UUID.randomUUID(), 1, sensitiveBody, UUID.randomUUID(), OffsetDateTime.now());
        ContentWorkflowService.ApprovalResponse approval =
                new ContentWorkflowService.ApprovalResponse(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        ApprovalStatus.REJECTED,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        sensitiveReason,
                        OffsetDateTime.now(),
                        OffsetDateTime.now());

        org.assertj.core.api.Assertions.assertThat(request.toString())
                .doesNotContain(sensitiveBody)
                .contains("[REDACTED]");
        org.assertj.core.api.Assertions.assertThat(version.toString())
                .doesNotContain(sensitiveBody)
                .contains("[REDACTED]");
        org.assertj.core.api.Assertions.assertThat(approval.toString())
                .doesNotContain(sensitiveReason)
                .contains("[REDACTED]");
    }

    private UUID createDraft(String subject, String channel, String body) throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/api/v1/stores/{storeId}/products/{productId}/contents", storeId, product.getId())
                                .with(login(subject)).with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"channel\":\"" + channel + "\",\"body\":\"" + body + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location",
                        org.hamcrest.Matchers.containsString("/api/v1/stores/" + storeId + "/contents/")))
                .andExpect(jsonPath("$.status", is("DRAFT")))
                .andExpect(jsonPath("$.currentVersion.body", is(body.trim())))
                .andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    private void submit(UUID contentId, String subject, long version) throws Exception {
        mockMvc.perform(post("/api/v1/stores/{storeId}/contents/{contentId}/submit", storeId, contentId)
                        .with(login(subject)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + version + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("IN_REVIEW")));
    }

    private void approve(UUID contentId, String subject, long version) throws Exception {
        mockMvc.perform(post("/api/v1/stores/{storeId}/contents/{contentId}/approve", storeId, contentId)
                        .with(login(subject)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + version + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("APPROVED")));
    }

    private AppUser user(String subject) {
        return new AppUser(
                UUID.randomUUID(),
                subject + "@content.test",
                subject,
                IdentityProvisioningService.GOOGLE_PROVIDER,
                subject);
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor
            login(String subject) {
        return oidcLogin().idToken(token -> token
                .subject(subject)
                .claim("email", subject + "@content.test")
                .claim("email_verified", true));
    }
}
