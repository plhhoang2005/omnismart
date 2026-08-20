package vn.omnismart.store;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import vn.omnismart.identity.AppUser;
import vn.omnismart.identity.AppUserRepository;
import vn.omnismart.identity.IdentityProvisioningService;
import vn.omnismart.audit.AuditAction;
import vn.omnismart.audit.AuditLogRepository;

@SpringBootTest
@AutoConfigureMockMvc
class StoreApiTests {

    private static final String OWNER_SUBJECT = "store-api-owner";
    private static final String STAFF_SUBJECT = "store-api-staff";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private StoreMemberRepository memberRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private UUID ownedStoreId;
    private UUID otherStoreId;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        memberRepository.deleteAll();
        storeRepository.deleteAll();
        userRepository.deleteAll();

        AppUser owner = userRepository.save(user(OWNER_SUBJECT, "owner@example.com", "Owner"));
        AppUser staff = userRepository.save(user(STAFF_SUBJECT, "staff@example.com", "Staff"));
        AppUser otherOwner = userRepository.save(user("other-owner", "other@example.com", "Other"));

        Store pendingStore = storeRepository.save(Store.pendingOnboarding(
                UUID.randomUUID(), "Owner's Store", "owners-store"));
        Store otherStore = storeRepository.save(new Store(
                UUID.randomUUID(), "Other Store", "other-store"));
        ownedStoreId = pendingStore.getId();
        otherStoreId = otherStore.getId();

        memberRepository.save(new StoreMember(ownedStoreId, owner.getId(), StoreRole.OWNER));
        memberRepository.save(new StoreMember(ownedStoreId, staff.getId(), StoreRole.STAFF));
        memberRepository.save(new StoreMember(otherStoreId, otherOwner.getId(), StoreRole.OWNER));
    }

    @Test
    void listsOnlyStoresBelongingToCurrentUser() throws Exception {
        mockMvc.perform(get("/api/v1/stores").with(login(OWNER_SUBJECT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(ownedStoreId.toString())))
                .andExpect(jsonPath("$[0].role", is("OWNER")));
    }

    @Test
    void doesNotRevealAnotherTenantStore() throws Exception {
        mockMvc.perform(get("/api/v1/stores/{storeId}", otherStoreId)
                        .with(login(OWNER_SUBJECT)))
                .andExpect(status().isNotFound());
    }

    @Test
    void createsConfirmedStoreOwnedByCurrentUserWithSafeSlug() throws Exception {
        mockMvc.perform(post("/api/v1/stores")
                        .with(login(OWNER_SUBJECT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  Cửa hàng Thực Tế  \"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/v1/stores/")))
                .andExpect(jsonPath("$.name", is("Cửa hàng Thực Tế")))
                .andExpect(jsonPath("$.slug", startsWith("cua-hang-thuc-te-")))
                .andExpect(jsonPath("$.status", is("ACTIVE")))
                .andExpect(jsonPath("$.onboardingCompleted", is(true)))
                .andExpect(jsonPath("$.role", is("OWNER")));

        org.assertj.core.api.Assertions.assertThat(auditLogRepository.findAll())
                .singleElement()
                .extracting(log -> log.getAction())
                .isEqualTo(AuditAction.STORE_CREATED);
    }

    @Test
    void rejectsClientSuppliedStoreIdInsteadOfTrustingIt() throws Exception {
        mockMvc.perform(post("/api/v1/stores")
                        .with(login(OWNER_SUBJECT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeId\":\"" + otherStoreId + "\",\"name\":\"Injected\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ownerConfirmsOnboardingByNamingStore() throws Exception {
        mockMvc.perform(patch("/api/v1/stores/{storeId}", ownedStoreId)
                        .with(login(OWNER_SUBJECT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  Omni Shop  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Omni Shop")))
                .andExpect(jsonPath("$.onboardingCompleted", is(true)));
    }

    @Test
    void staffCannotChangeStoreConfiguration() throws Exception {
        mockMvc.perform(patch("/api/v1/stores/{storeId}", ownedStoreId)
                        .with(login(STAFF_SUBJECT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Changed by staff\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void archiveRequiresCompletedOnboardingAndExactNameConfirmation() throws Exception {
        mockMvc.perform(patch("/api/v1/stores/{storeId}", ownedStoreId)
                        .with(login(OWNER_SUBJECT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ARCHIVED\",\"confirmationName\":\"Owner's Store\"}"))
                .andExpect(status().isBadRequest());

        confirmStoreName("Omni Shop");

        mockMvc.perform(patch("/api/v1/stores/{storeId}", ownedStoreId)
                        .with(login(OWNER_SUBJECT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ARCHIVED\",\"confirmationName\":\"Wrong name\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/v1/stores/{storeId}", ownedStoreId)
                        .with(login(OWNER_SUBJECT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ARCHIVED\",\"confirmationName\":\"Omni Shop\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ARCHIVED")))
                .andExpect(jsonPath("$.archivedAt").isNotEmpty());
    }

    @Test
    void storeCannotBeHardDeletedThroughApi() throws Exception {
        mockMvc.perform(delete("/api/v1/stores/{storeId}", ownedStoreId)
                        .with(login(OWNER_SUBJECT))
                        .with(csrf()))
                .andExpect(status().isMethodNotAllowed());
    }

    private void confirmStoreName(String name) throws Exception {
        mockMvc.perform(patch("/api/v1/stores/{storeId}", ownedStoreId)
                        .with(login(OWNER_SUBJECT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isOk());
    }

    private AppUser user(String subject, String email, String displayName) {
        return new AppUser(
                UUID.randomUUID(),
                email,
                displayName,
                IdentityProvisioningService.GOOGLE_PROVIDER,
                subject);
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor
            login(String subject) {
        return oidcLogin().idToken(token -> token
                .subject(subject)
                .claim("email", subject + "@example.com")
                .claim("email_verified", true));
    }
}
