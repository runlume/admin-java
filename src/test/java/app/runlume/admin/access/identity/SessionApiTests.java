package app.runlume.admin.access.identity;

import app.runlume.admin.support.PostgresTestSupport;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 注册、会话、退出与 CSRF 的端到端行为。
 *
 * <p>用例按真实前端的方式工作：先取 {@code GET /api/v1/csrf}，再在写请求回传同名请求头与
 * Cookie，避免测试绕过 CSRF 保护。</p>
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 11:40
 */
class SessionApiTests extends PostgresTestSupport {

    @Autowired
    private MockMvc mockMvc;

    private Cookie csrfCookie;
    private String csrfToken;

    @BeforeEach
    void fetchCsrfToken() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        csrfCookie = result.getResponse().getCookie("XSRF-TOKEN");
        csrfToken = JsonPath.read(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8),
                "$.token"
        );
        assertThat(csrfCookie).isNotNull();
        assertThat(csrfToken).isNotBlank();
    }

    @Test
    void registerEstablishesSessionAndLogoutRevokesIt() throws Exception {
        String email = "session-" + UUID.randomUUID() + "@runlume.local";
        MvcResult registration = mockMvc.perform(post("/api/v1/auth/register")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.permissions").isArray())
                .andReturn();
        Cookie session = registration.getResponse().getCookie("ADMIN_SESSION");
        assertThat(session).isNotNull();

        mockMvc.perform(get("/api/v1/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(email));

        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(session, csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/me").cookie(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registeredLocalAccountReachesWorkspaceScopedBusinessData() throws Exception {
        String email = "workspace-" + UUID.randomUUID() + "@runlume.local";
        MvcResult registration = mockMvc.perform(post("/api/v1/auth/register")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email)))
                .andExpect(status().isCreated())
                .andReturn();
        Cookie session = registration.getResponse().getCookie("ADMIN_SESSION");
        assertThat(session).isNotNull();

        mockMvc.perform(get("/api/v1/notices").cookie(session))
                .andExpect(status().isOk());
    }

    @Test
    void writeRequestWithoutCsrfTokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("no-csrf@runlume.local")))
                .andExpect(status().isForbidden());
    }

    @Test
    void duplicateEmailIsRejected() throws Exception {
        String email = "duplicate-" + UUID.randomUUID() + "@runlume.local";
        mockMvc.perform(post("/api/v1/auth/register")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/auth/register")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void shortPasswordIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"short@runlume.local\","
                                + "\"displayName\":\"口令过短\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private static String registerBody(String email) {
        return """
                {"email":"%s","displayName":"会话测试","password":"runlume-password"}
                """.formatted(email);
    }
}
