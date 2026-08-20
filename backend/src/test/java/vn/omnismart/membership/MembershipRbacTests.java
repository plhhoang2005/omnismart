package vn.omnismart.membership;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

import vn.omnismart.audit.AuditAction;
import vn.omnismart.audit.AuditLogRepository;
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
class MembershipRbacTests {

    private static final String OWNER_SUBJECT = "rbac-owner";
    private static final String STAFF_SUBJECT = "rbac-staff";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private StoreMemberRepository memberRepository;

    @Autowired
    private MembershipInvitationRepository invitationRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private UUID storeId;
    private UUID ownerId;
    private UUID staffId;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        invitationRepository.deleteAll();
        memberRepository.deleteAll();
        storeRepository.deleteAll();
        userRepository.deleteAll();

        AppUser owner = userRepository.save(user(OWNER_SUBJECT, "owner@example.com", "Owner"));
        AppUser staff = userRepository.save(user(STAFF_SUBJECT, "staff@example.com", "Staff"));
        Store store = storeRepository.save(new Store(UUID.randomUUID(), "Omni Shop", "omni-shop"));
        storeId = store.getId();
        ownerId = owner.getId();
        staffId = staff.getId();
        memberRepository.save(new StoreMember(storeId, ownerId, StoreRole.OWNER));
        memberRepository.save(new StoreMember(storeId, staffId, StoreRole.STAFF));
    }

    @Test
    void onlyOwnerCanListMembers() throws Exception {
        mockMvc.perform(get("/api/v1/stores/{storeId}/members", storeId)
                        .with(login(OWNER_SUBJECT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].displayName", is("Owner")))
                .andExpect(jsonPath("$[1].displayName", is("Staff")));

        mockMvc.perform(get("/api/v1/stores/{storeId}/members", storeId)
                        .with(login(STAFF_SUBJECT)))
                .andExpect(status().isNotFound());
    }

    @Test
    void roleChangeRequiresExplicitStoreNameAndCreatesAuditEvent() throws Exception {
        mockMvc.perform(patch("/api/v1/stores/{storeId}/members/{userId}", storeId, staffId)
                        .with(login(OWNER_SUBJECT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"OWNER\",\"confirmationName\":\"Wrong Shop\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/v1/stores/{storeId}/members/{userId}", storeId, staffId)
                        .with(login(OWNER_SUBJECT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"OWNER\",\"confirmationName\":\"Omni Shop\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role", is("OWNER")));

        org.assertj.core.api.Assertions.assertThat(
                        memberRepository.findByStoreIdAndUserId(storeId, staffId))
                .get()
                .extracting(StoreMember::getRole)
                .isEqualTo(StoreRole.OWNER);
        org.assertj.core.api.Assertions.assertThat(
                        auditLogRepository.findByStoreIdOrderByCreatedAtAsc(storeId))
                .singleElement()
                .satisfies(log -> {
                    org.assertj.core.api.Assertions.assertThat(log.getAction())
                            .isEqualTo(AuditAction.MEMBER_ROLE_CHANGED);
                    org.assertj.core.api.Assertions.assertThat(log.getResourceId()).isEqualTo(staffId);
                    org.assertj.core.api.Assertions.assertThat(log.getDetails())
                            .isEqualTo("from=STAFF,to=OWNER");
                });
    }

    @Test
    void invalidRoleIsRejectedInsteadOfBecomingFreeFormData() throws Exception {
        mockMvc.perform(patch("/api/v1/stores/{storeId}/members/{userId}", storeId, staffId)
                        .with(login(OWNER_SUBJECT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\",\"confirmationName\":\"Omni Shop\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void lastOwnerCannotBeDowngradedOrRevoked() throws Exception {
        mockMvc.perform(patch("/api/v1/stores/{storeId}/members/{userId}", storeId, ownerId)
                        .with(login(OWNER_SUBJECT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"STAFF\",\"confirmationName\":\"Omni Shop\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/v1/stores/{storeId}/members/{userId}", storeId, ownerId)
                        .with(login(OWNER_SUBJECT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmationName\":\"Omni Shop\"}"))
                .andExpect(status().isConflict());

        org.assertj.core.api.Assertions.assertThat(
                        memberRepository.findByStoreIdAndUserId(storeId, ownerId))
                .isPresent();
    }

    @Test
    void ownerCanRevokeStaffOnlyAfterConfirmationAndActionIsAudited() throws Exception {
        mockMvc.perform(delete("/api/v1/stores/{storeId}/members/{userId}", storeId, staffId)
                        .with(login(OWNER_SUBJECT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmationName\":\"Wrong Shop\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/v1/stores/{storeId}/members/{userId}", storeId, staffId)
                        .with(login(OWNER_SUBJECT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmationName\":\"Omni Shop\"}"))
                .andExpect(status().isNoContent());

        org.assertj.core.api.Assertions.assertThat(
                        memberRepository.findByStoreIdAndUserId(storeId, staffId))
                .isEmpty();
        org.assertj.core.api.Assertions.assertThat(
                        auditLogRepository.findByStoreIdOrderByCreatedAtAsc(storeId))
                .singleElement()
                .satisfies(log -> org.assertj.core.api.Assertions.assertThat(log.getAction())
                        .isEqualTo(AuditAction.MEMBER_REVOKED));
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
