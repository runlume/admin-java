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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 成员与角色接口的授权边界。
 *
 * <p>授权注解里的权限码必须与 {@link PermissionCatalog} 的目录一致：写成别的形状时
 * （例如点分制之前的 {@code role:view}）任何账号都拿不到该授权，端点整体不可用。</p>
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/19 23:12
 */
class MemberManagementApiTests extends PostgresTestSupport {

    private static final String PASSWORD = "runlume-password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdminIdentity identity;

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
    void rolesFollowTheCatalogRoleViewPermission() throws Exception {
        AdminUserView account = identity.register(
                "roles-" + UUID.randomUUID() + "@runlume.local",
                "角色目录",
                PASSWORD
        );

        identity.changeRoles(account.workspaceId(), account.id(), Set.of("admin"));
        mockMvc.perform(get("/api/v1/roles").cookie(login(account.email())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == 'admin')]").exists())
                .andExpect(jsonPath("$[?(@.code == 'member')]").exists());

        identity.changeRoles(account.workspaceId(), account.id(), Set.of("member"));
        mockMvc.perform(get("/api/v1/roles").cookie(login(account.email())))
                .andExpect(status().isForbidden());
    }

    private Cookie login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        Cookie session = result.getResponse().getCookie("ADMIN_SESSION");
        assertThat(session).isNotNull();
        return session;
    }
}
