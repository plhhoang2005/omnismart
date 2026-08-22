package vn.omnismart.membership;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import vn.omnismart.membership.InvitationService.CreatedInvitationResponse;
import vn.omnismart.store.StoreRole;

class InvitationSecretRedactionTests {

    private static final String RAW_TOKEN = "raw-token-must-never-appear-in-to-string";
    private static final String EMAIL = "invitee@example.com";

    @Test
    void requestAndResponseStringRepresentationsRedactRawToken() {
        InvitationController.InvitationTokenRequest request =
                new InvitationController.InvitationTokenRequest(RAW_TOKEN);
        InvitationController.CreateInvitationRequest createRequest =
                new InvitationController.CreateInvitationRequest(EMAIL, StoreRole.STAFF, "Omni Shop");
        CreatedInvitationResponse response = new CreatedInvitationResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Omni Shop",
                EMAIL,
                StoreRole.STAFF,
                InvitationStatus.PENDING,
                OffsetDateTime.now().plusDays(3),
                RAW_TOKEN,
                "MANUAL");

        assertThat(request.toString()).contains("[REDACTED]").doesNotContain(RAW_TOKEN);
        assertThat(createRequest.toString()).contains("[REDACTED]").doesNotContain(EMAIL, "Omni Shop");
        assertThat(response.toString()).contains("[REDACTED]").doesNotContain(RAW_TOKEN, EMAIL);
    }
}
