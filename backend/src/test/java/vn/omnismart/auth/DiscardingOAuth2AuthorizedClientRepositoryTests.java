package vn.omnismart.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;

class DiscardingOAuth2AuthorizedClientRepositoryTests {

    private final DiscardingOAuth2AuthorizedClientRepository repository =
            new DiscardingOAuth2AuthorizedClientRepository();

    @Test
    void neverLoadsOrPersistsProviderTokens() {
        Authentication principal = mock(Authentication.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        OAuth2AuthorizedClient authorizedClient = mock(OAuth2AuthorizedClient.class);

        assertThat(repository.<OAuth2AuthorizedClient>loadAuthorizedClient("google", principal, request))
                .isNull();
        assertThatCode(() -> repository.saveAuthorizedClient(
                authorizedClient, principal, request, response))
                .doesNotThrowAnyException();
        assertThatCode(() -> repository.removeAuthorizedClient(
                "google", principal, request, response))
                .doesNotThrowAnyException();
    }
}
