package vn.omnismart.catalog;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
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

@SpringBootTest
@AutoConfigureMockMvc
class ProductCrudTests {

    private static final String OWNER_SUBJECT = "catalog-owner";
    private static final String OTHER_SUBJECT = "catalog-other";

    @Autowired MockMvc mockMvc;
    @Autowired AppUserRepository userRepository;
    @Autowired StoreRepository storeRepository;
    @Autowired StoreMemberRepository memberRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ProductMediaRepository mediaRepository;
    @Autowired MembershipInvitationRepository invitationRepository;
    @Autowired AuditLogRepository auditLogRepository;

    private UUID storeId;
    private UUID otherStoreId;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        invitationRepository.deleteAll();
        mediaRepository.deleteAll();
        productRepository.deleteAll();
        memberRepository.deleteAll();
        storeRepository.deleteAll();
        userRepository.deleteAll();

        AppUser owner = userRepository.save(user(OWNER_SUBJECT, "owner@catalog.test"));
        AppUser other = userRepository.save(user(OTHER_SUBJECT, "other@catalog.test"));
        Store store = storeRepository.save(new Store(UUID.randomUUID(), "Catalog Store", "catalog-store"));
        Store otherStore = storeRepository.save(new Store(UUID.randomUUID(), "Other Store", "other-catalog-store"));
        storeId = store.getId();
        otherStoreId = otherStore.getId();
        memberRepository.save(new StoreMember(storeId, owner.getId(), StoreRole.OWNER));
        memberRepository.save(new StoreMember(otherStoreId, other.getId(), StoreRole.OWNER));
    }

    @Test
    void createsProductWithNormalizedSkuExactMoneyAndAudit() throws Exception {
        mockMvc.perform(post("/api/v1/stores/{storeId}/products", storeId)
                        .with(login(OWNER_SUBJECT)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"  sku-001  ","name":"  Coffee  ","description":" Arabica ",
                                 "price":125000.50,"currency":"VND","inventoryQuantity":12}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku", is("SKU-001")))
                .andExpect(jsonPath("$.name", is("Coffee")))
                .andExpect(jsonPath("$.price", is(125000.50)))
                .andExpect(jsonPath("$.status", is("ACTIVE")))
                .andExpect(jsonPath("$.version", is(0)));

        org.assertj.core.api.Assertions.assertThat(productRepository.findAll())
                .singleElement()
                .satisfies(product -> {
                    org.assertj.core.api.Assertions.assertThat(product.getPrice())
                            .isEqualByComparingTo(new BigDecimal("125000.50"));
                    org.assertj.core.api.Assertions.assertThat(product.getStoreId()).isEqualTo(storeId);
                });
        org.assertj.core.api.Assertions.assertThat(auditLogRepository.findByStoreIdOrderByCreatedAtAsc(storeId))
                .singleElement()
                .extracting(log -> log.getAction())
                .isEqualTo(AuditAction.PRODUCT_CREATED);
    }

    @Test
    void rejectsNegativeValuesInvalidCurrencyAndDuplicateSkuWithinStore() throws Exception {
        createProduct("SKU-1", "Coffee");

        mockMvc.perform(post("/api/v1/stores/{storeId}/products", storeId)
                        .with(login(OWNER_SUBJECT)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"sku-1","name":"Duplicate","price":1,"currency":"VND","inventoryQuantity":1}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("PRODUCT_SKU_CONFLICT")));

        mockMvc.perform(post("/api/v1/stores/{storeId}/products", storeId)
                        .with(login(OWNER_SUBJECT)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"BAD","name":"Bad","price":-1,"currency":"EUR","inventoryQuantity":-1}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pendingOnboardingStoreCannotUseCatalogMutations() throws Exception {
        Store pending = storeRepository.save(Store.pendingOnboarding(
                UUID.randomUUID(), "Pending Store", "pending-store"));
        AppUser owner = userRepository.findByProviderAndProviderSubject(
                        IdentityProvisioningService.GOOGLE_PROVIDER, OWNER_SUBJECT)
                .orElseThrow();
        memberRepository.save(new StoreMember(pending.getId(), owner.getId(), StoreRole.OWNER));

        mockMvc.perform(post("/api/v1/stores/{storeId}/products", pending.getId())
                        .with(login(OWNER_SUBJECT)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"WAIT-1","name":"Wait","price":1,
                                 "currency":"VND","inventoryQuantity":1}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("STORE_ONBOARDING_REQUIRED")))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.path", is(
                        "/api/v1/stores/" + pending.getId() + "/products")));
    }

    @Test
    void listsWithPaginationSearchAndStatusFilter() throws Exception {
        createProduct("COFFEE-1", "Arabica Coffee");
        Product archived = productRepository.save(new Product(
                UUID.randomUUID(), storeId, "TEA-1", "Green Tea", null,
                new BigDecimal("20.00"), ProductCurrency.VND, 2));
        archived.archive();
        productRepository.saveAndFlush(archived);

        mockMvc.perform(get("/api/v1/stores/{storeId}/products", storeId)
                        .with(login(OWNER_SUBJECT))
                        .param("page", "0").param("size", "1")
                        .param("search", "coffee").param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].sku", is("COFFEE-1")))
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.page", is(0)));
    }

    @Test
    void invalidQueryParametersUseStableErrorContract() throws Exception {
        mockMvc.perform(get("/api/v1/stores/{storeId}/products", storeId)
                        .with(login(OWNER_SUBJECT))
                        .param("size", "0")
                        .param("status", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("REQUEST_PARAMETER_INVALID")))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void staleVersionReturnsProductVersionConflict() throws Exception {
        Product product = createProduct("LOCK-1", "Versioned");

        mockMvc.perform(patch("/api/v1/stores/{storeId}/products/{productId}", storeId, product.getId())
                        .with(login(OWNER_SUBJECT)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"name\":\"First update\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version", is(1)));

        mockMvc.perform(patch("/api/v1/stores/{storeId}/products/{productId}", storeId, product.getId())
                        .with(login(OWNER_SUBJECT)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"name\":\"Stale update\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("PRODUCT_VERSION_CONFLICT")));

        org.assertj.core.api.Assertions.assertThat(
                        auditLogRepository.findByStoreIdOrderByCreatedAtAsc(storeId))
                .extracting(log -> log.getAction())
                .containsExactly(AuditAction.PRODUCT_UPDATED);
    }

    @Test
    void updateCanExplicitlyClearDescription() throws Exception {
        Product product = productRepository.saveAndFlush(new Product(
                UUID.randomUUID(), storeId, "DESC-1", "Description", "Old text",
                new BigDecimal("10.00"), ProductCurrency.VND, 5));

        mockMvc.perform(patch("/api/v1/stores/{storeId}/products/{productId}", storeId, product.getId())
                        .with(login(OWNER_SUBJECT)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"description\":\"   \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").doesNotExist());

        org.assertj.core.api.Assertions.assertThat(productRepository.findById(product.getId()))
                .get()
                .extracting(Product::getDescription)
                .isNull();
    }

    @Test
    void anotherTenantCannotReadUpdateOrArchiveProduct() throws Exception {
        Product product = createProduct("PRIVATE-1", "Private");

        mockMvc.perform(get("/api/v1/stores/{storeId}/products/{productId}", storeId, product.getId())
                        .with(login(OTHER_SUBJECT)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/stores/{storeId}/products/{productId}", otherStoreId, product.getId())
                        .with(login(OTHER_SUBJECT)))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/v1/stores/{storeId}/products/{productId}", storeId, product.getId())
                        .with(login(OTHER_SUBJECT)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"name\":\"Stolen\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void archiveRequiresSkuConfirmationAndKeepsProductForAuditability() throws Exception {
        Product product = createProduct("ARCHIVE-1", "Archive me");

        mockMvc.perform(delete("/api/v1/stores/{storeId}/products/{productId}", storeId, product.getId())
                        .with(login(OWNER_SUBJECT)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"confirmationSku\":\"WRONG\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("PRODUCT_ARCHIVE_CONFIRMATION_MISMATCH")));

        mockMvc.perform(delete("/api/v1/stores/{storeId}/products/{productId}", storeId, product.getId())
                        .with(login(OWNER_SUBJECT)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"confirmationSku\":\"archive-1\"}"))
                .andExpect(status().isNoContent());

        org.assertj.core.api.Assertions.assertThat(productRepository.findById(product.getId()))
                .get()
                .extracting(Product::getStatus)
                .isEqualTo(ProductStatus.ARCHIVED);
        org.assertj.core.api.Assertions.assertThat(auditLogRepository.findByStoreIdOrderByCreatedAtAsc(storeId))
                .extracting(log -> log.getAction())
                .contains(AuditAction.PRODUCT_ARCHIVED);
    }

    private Product createProduct(String sku, String name) {
        return productRepository.saveAndFlush(new Product(
                UUID.randomUUID(), storeId, sku, name, null,
                new BigDecimal("10.00"), ProductCurrency.VND, 5));
    }

    private AppUser user(String subject, String email) {
        return new AppUser(
                UUID.randomUUID(), email, subject, IdentityProvisioningService.GOOGLE_PROVIDER, subject);
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor
            login(String subject) {
        return oidcLogin().idToken(token -> token
                .subject(subject)
                .claim("email", subject + "@catalog.test")
                .claim("email_verified", true));
    }
}
