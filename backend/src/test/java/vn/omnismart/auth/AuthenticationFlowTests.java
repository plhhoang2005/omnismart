package vn.omnismart.auth;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;

import vn.omnismart.identity.AppUser;
import vn.omnismart.identity.AppUserRepository;
import vn.omnismart.identity.IdentityProvisioningService;
import vn.omnismart.store.Store;
import vn.omnismart.store.StoreMember;
import vn.omnismart.store.StoreMemberRepository;
import vn.omnismart.store.StoreRepository;
import vn.omnismart.store.StoreRole;

@SpringBootTest
@AutoConfigureMockMvc
class AuthenticationFlowTests {

    private static final String OWNER_SUBJECT = "google-owner-subject";
    private static final String STAFF_SUBJECT = "google-staff-subject";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private StoreMemberRepository memberRepository;

    private UUID ownedStoreId;
    private UUID otherStoreId;

    @BeforeEach
    void setUpMemberships() {
        memberRepository.deleteAll();
        storeRepository.deleteAll();
        userRepository.deleteAll();

        AppUser owner = userRepository.save(new AppUser(
                UUID.randomUUID(),
                "owner@example.com",
                "Store Owner",
                IdentityProvisioningService.GOOGLE_PROVIDER,
                OWNER_SUBJECT));
        AppUser staff = userRepository.save(new AppUser(
                UUID.randomUUID(),
                "staff@example.com",
                "Store Staff",
                IdentityProvisioningService.GOOGLE_PROVIDER,
                STAFF_SUBJECT));

        Store ownedStore = storeRepository.save(
                new Store(UUID.randomUUID(), "Owned Store", "owned-store"));
        Store otherStore = storeRepository.save(
                new Store(UUID.randomUUID(), "Other Store", "other-store"));
        ownedStoreId = ownedStore.getId();
        otherStoreId = otherStore.getId();

        memberRepository.save(new StoreMember(ownedStoreId, owner.getId(), StoreRole.OWNER));
        memberRepository.save(new StoreMember(ownedStoreId, staff.getId(), StoreRole.STAFF));
    }

    @Test
    void authenticatedUserReceivesProfileAndCorrectMembership() throws Exception {
        mockMvc.perform(get("/api/v1/me").with(googleLogin(OWNER_SUBJECT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("owner@example.com")))
                .andExpect(jsonPath("$.memberships", hasSize(1)))
                .andExpect(jsonPath("$.memberships[0].storeId", is(ownedStoreId.toString())))
                .andExpect(jsonPath("$.memberships[0].role", is("OWNER")));
    }

    @Test
    void unauthenticatedApiRequestReceivesUnauthorizedInsteadOfLoginHtml() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("AUTHENTICATION_REQUIRED")))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.path", is("/api/v1/me")));
    }

    @Test
    void membersCanReadStoreButOnlyOwnerCanUpdateIt() throws Exception {
        mockMvc.perform(get("/api/v1/stores/{storeId}", ownedStoreId)
                        .with(googleLogin(OWNER_SUBJECT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role", is("OWNER")));

        mockMvc.perform(get("/api/v1/stores/{storeId}", ownedStoreId)
                        .with(googleLogin(STAFF_SUBJECT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role", is("STAFF")));

        mockMvc.perform(patch("/api/v1/stores/{storeId}", ownedStoreId)
                        .with(googleLogin(STAFF_SUBJECT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Unauthorized rename\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void crossStoreAccessIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/stores/{storeId}", otherStoreId)
                        .with(googleLogin(OWNER_SUBJECT)))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectedGoogleCallbackReturnsToFrontendWithSafeError() throws Exception {
        mockMvc.perform(get("/login/oauth2/code/google")
                        .param("error", "access_denied")
                        .param("error_description", "User denied access")
                        .param("state", "rejected-state"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost:5173/login?error=oauth"));
    }

    @Test
    void logoutRequiresCsrfAndClearsTheServerSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/v1/auth/logout")
                        .with(googleLogin(OWNER_SUBJECT))
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("JSESSIONID", 0));
        org.assertj.core.api.Assertions.assertThat(session.isInvalid()).isTrue();

        mockMvc.perform(post("/api/v1/auth/logout").with(googleLogin(OWNER_SUBJECT)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("ACCESS_DENIED")));
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor
            googleLogin(String subject) {
        return oidcLogin().idToken(token -> token
                .subject(subject)
                .claim("email", subject + "@example.com")
                .claim("email_verified", true));
    }
}
