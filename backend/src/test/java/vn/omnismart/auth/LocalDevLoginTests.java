package vn.omnismart.auth;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import vn.omnismart.identity.AppUserRepository;
import vn.omnismart.store.StoreMemberRepository;
import vn.omnismart.store.StoreRepository;

@SpringBootTest(properties = "omnismart.security.dev-login.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("local")
class LocalDevLoginTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private StoreMemberRepository memberRepository;

    @BeforeEach
    void clearData() {
        memberRepository.deleteAll();
        storeRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void enabledLocalLoginCreatesAReusableAuthenticatedSession() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/dev-login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"Teammate@Example.com\",\"displayName\":\"Team Tester\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("teammate@example.com")))
                .andExpect(jsonPath("$.displayName", is("Team Tester")))
                .andExpect(jsonPath("$.memberships[0].role", is("OWNER")))
                .andReturn();

        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        mockMvc.perform(get("/api/v1/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("teammate@example.com")));
    }

    @Test
    void localLoginStillRequiresCsrfAndValidInput() throws Exception {
        mockMvc.perform(post("/api/v1/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"tester@example.com\",\"displayName\":\"Tester\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/auth/dev-login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"displayName\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
