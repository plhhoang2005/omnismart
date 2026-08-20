package vn.omnismart.membership;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
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
class InvitationFlowTests {

    private static final String OWNER_SUBJECT = "invitation-owner";
    private static final String STAFF_SUBJECT = "invitation-staff";
    private static final String INVITEE_SUBJECT = "invitation-recipient";
    private static final String OUTSIDER_SUBJECT = "invitation-outsider";

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
    private UUID inviteeId;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        invitationRepository.deleteAll();
        memberRepository.deleteAll();
        storeRepository.deleteAll();
        userRepository.deleteAll();

        AppUser owner = userRepository.save(user(OWNER_SUBJECT, "owner@example.com", "Owner"));
        AppUser staff = userRepository.save(user(STAFF_SUBJECT, "staff@example.com", "Staff"));
        AppUser invitee = userRepository.save(user(
                INVITEE_SUBJECT, "invitee@example.com", "Invitee"));
        userRepository.save(user(OUTSIDER_SUBJECT, "outsider@example.com", "Outsider"));
        Store store = storeRepository.save(new Store(UUID.randomUUID(), "Omni Shop", "omni-shop"));
        storeId = store.getId();
        ownerId = owner.getId();
        inviteeId = invitee.getId();
        memberRepository.save(new StoreMember(storeId, owner.getId(), StoreRole.OWNER));
        memberRepository.save(new StoreMember(storeId, staff.getId(), StoreRole.STAFF));
    }

    @Test
    void onlyOwnerCanInviteAndOwnerInvitationRequiresExplicitConfirmation() throws Exception {
        mockMvc.perform(post("/api/v1/stores/{storeId}/invitations", storeId)
                        .with(login(STAFF_SUBJECT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invitationJson("invitee@example.com", "STAFF", null)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/stores/{storeId}/invitations", storeId)
                        .with(login(OWNER_SUBJECT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invitationJson("invitee@example.com", "OWNER", "Wrong Shop")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/stores/{storeId}/invitations", storeId)
                        .with(login(OWNER_SUBJECT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invitationJson("invitee@example.com", "OWNER", "Omni Shop")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role", is("OWNER")))
                .andExpect(jsonPath("$.delivery", is("MANUAL")))
                .andExpect(jsonPath("$.invitationToken").isNotEmpty());
    }

    @Test
    void rejectsExistingMemberAndDuplicatePendingInvitation() throws Exception {
        mockMvc.perform(post("/api/v1/stores/{storeId}/invitations", storeId)
                        .with(login(OWNER_SUBJECT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invitationJson("staff@example.com", "STAFF", null)))
                .andExpect(status().isConflict());

        createInvitation("Invitee@Example.com", "STAFF", null);

        mockMvc.perform(post("/api/v1/stores/{storeId}/invitations", storeId)
                        .with(login(OWNER_SUBJECT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invitationJson("invitee@example.com", "STAFF", null)))
                .andExpect(status().isConflict());
        org.assertj.core.api.Assertions.assertThat(invitationRepository.count()).isEqualTo(1);
    }

    @Test
    void tokenIsStoredOnlyAsHashAndAcceptedExactlyOnceByMatchingEmail() throws Exception {
        CreatedInvitation created = createInvitation("invitee@example.com", "STAFF", null);
        MembershipInvitation persisted = invitationRepository.findById(created.id()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(persisted.getTokenHash())
                .hasSize(64)
                .isNotEqualTo(created.token());

        mockMvc.perform(post("/api/v1/invitations/{id}/accept", created.id())
                        .with(login(INVITEE_SUBJECT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenJson(created.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ACCEPTED")))
                .andExpect(jsonPath("$.role", is("STAFF")));

        mockMvc.perform(post("/api/v1/invitations/{id}/accept", created.id())
                        .with(login(INVITEE_SUBJECT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenJson(created.token())))
                .andExpect(status().isConflict());

        org.assertj.core.api.Assertions.assertThat(
                        memberRepository.findByStoreIdAndUserId(storeId, inviteeId))
                .get()
                .extracting(StoreMember::getRole)
                .isEqualTo(StoreRole.STAFF);
        org.assertj.core.api.Assertions.assertThat(
                        auditLogRepository.findByStoreIdOrderByCreatedAtAsc(storeId))
                .extracting(log -> log.getAction())
                .containsExactly(AuditAction.INVITATION_CREATED, AuditAction.INVITATION_ACCEPTED);
        org.assertj.core.api.Assertions.assertThat(
                        auditLogRepository.findByStoreIdOrderByCreatedAtAsc(storeId))
                .allSatisfy(log -> org.assertj.core.api.Assertions.assertThat(log.getDetails())
                        .doesNotContain(created.token()));
    }

    @Test
    void recipientMustMatchEmailAndCanExplicitlyDecline() throws Exception {
        CreatedInvitation created = createInvitation("invitee@example.com", "STAFF", null);

        mockMvc.perform(post("/api/v1/invitations/{id}/decline", created.id())
                        .with(login(OUTSIDER_SUBJECT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenJson(created.token())))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/invitations/{id}/decline", created.id())
                        .with(login(INVITEE_SUBJECT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenJson(created.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("DECLINED")));

        org.assertj.core.api.Assertions.assertThat(
                        memberRepository.findByStoreIdAndUserId(storeId, inviteeId))
                .isEmpty();
    }

    @Test
    void expiredInvitationCannotBeAcceptedAndIsPersistedAsExpired() throws Exception {
        String rawToken = "expired-secure-test-token";
        MembershipInvitation expired = invitationRepository.save(new MembershipInvitation(
                UUID.randomUUID(),
                storeId,
                "invitee@example.com",
                StoreRole.STAFF,
                sha256(rawToken),
                ownerId,
                OffsetDateTime.now().minusMinutes(1)));

        mockMvc.perform(post("/api/v1/invitations/{id}/accept", expired.getId())
                        .with(login(INVITEE_SUBJECT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenJson(rawToken)))
                .andExpect(status().isGone());

        org.assertj.core.api.Assertions.assertThat(
                        invitationRepository.findById(expired.getId()))
                .get()
                .extracting(MembershipInvitation::getStatus)
                .isEqualTo(InvitationStatus.EXPIRED);
        org.assertj.core.api.Assertions.assertThat(
                        auditLogRepository.findByStoreIdOrderByCreatedAtAsc(storeId))
                .singleElement()
                .satisfies(log -> org.assertj.core.api.Assertions.assertThat(log.getAction())
                        .isEqualTo(AuditAction.INVITATION_EXPIRED));
    }

    @Test
    void userAndOwnerCanListRelevantInvitationsWithoutReceivingToken() throws Exception {
        createInvitation("invitee@example.com", "STAFF", null);

        mockMvc.perform(get("/api/v1/invitations").with(login(INVITEE_SUBJECT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].email", is("invitee@example.com")))
                .andExpect(jsonPath("$[0].invitationToken").doesNotExist());

        mockMvc.perform(get("/api/v1/stores/{storeId}/invitations", storeId)
                        .with(login(OWNER_SUBJECT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status", is("PENDING")))
                .andExpect(jsonPath("$[0].invitationToken").doesNotExist());
    }

    @Test
    void ownerCanRevokePendingInvitationOnlyWithEmailConfirmation() throws Exception {
        CreatedInvitation created = createInvitation("invitee@example.com", "STAFF", null);

        mockMvc.perform(delete(
                            "/api/v1/stores/{storeId}/invitations/{invitationId}",
                            storeId, created.id())
                        .with(login(OWNER_SUBJECT)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmationEmail\":\"wrong@example.com\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete(
                            "/api/v1/stores/{storeId}/invitations/{invitationId}",
                            storeId, created.id())
                        .with(login(OWNER_SUBJECT)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmationEmail\":\"Invitee@Example.com\"}"))
                .andExpect(status().isNoContent());

        org.assertj.core.api.Assertions.assertThat(invitationRepository.findById(created.id()))
                .get()
                .extracting(MembershipInvitation::getStatus)
                .isEqualTo(InvitationStatus.REVOKED);
        mockMvc.perform(post("/api/v1/invitations/{id}/accept", created.id())
                        .with(login(INVITEE_SUBJECT)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenJson(created.token())))
                .andExpect(status().isConflict());
        org.assertj.core.api.Assertions.assertThat(
                        auditLogRepository.findByStoreIdOrderByCreatedAtAsc(storeId))
                .extracting(log -> log.getAction())
                .contains(AuditAction.INVITATION_REVOKED);
    }

    @Test
    void pendingOnboardingStoreCannotInviteMembers() throws Exception {
        Store pending = storeRepository.save(Store.pendingOnboarding(
                UUID.randomUUID(), "Pending Store", "pending-invitation-store"));
        memberRepository.save(new StoreMember(pending.getId(), ownerId, StoreRole.OWNER));

        mockMvc.perform(post("/api/v1/stores/{storeId}/invitations", pending.getId())
                        .with(login(OWNER_SUBJECT)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invitationJson("invitee@example.com", "STAFF", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("STORE_ONBOARDING_REQUIRED")));
    }

    private CreatedInvitation createInvitation(
            String email,
            String role,
            String confirmationName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/stores/{storeId}/invitations", storeId)
                        .with(login(OWNER_SUBJECT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invitationJson(email, role, confirmationName)))
                .andExpect(status().isCreated())
                .andReturn();
        String json = result.getResponse().getContentAsString();
        return new CreatedInvitation(
                UUID.fromString(JsonPath.read(json, "$.id")),
                JsonPath.read(json, "$.invitationToken"));
    }

    private String invitationJson(String email, String role, String confirmationName) {
        String confirmation = confirmationName == null
                ? "null"
                : "\"" + confirmationName + "\"";
        return "{\"email\":\"" + email + "\",\"role\":\"" + role
                + "\",\"confirmationName\":" + confirmation + "}";
    }

    private String tokenJson(String token) {
        return "{\"token\":\"" + token + "\"}";
    }

    private String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                        .digest(value.getBytes(StandardCharsets.UTF_8)));
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

    private record CreatedInvitation(UUID id, String token) {
    }
}
