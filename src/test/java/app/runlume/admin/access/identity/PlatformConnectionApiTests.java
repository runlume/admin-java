package app.runlume.admin.access.identity;

import app.runlume.admin.support.PostgresTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.json.JsonCompareMode;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 平台联机端点的同源公开契约。
 *
 * @author 树深技术
 * @since 0.1 at 2026/9/18 11:40
 */
class PlatformConnectionApiTests extends PostgresTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void isAnonymousNoStoreAndBooleanOnly() throws Exception {
        mockMvc.perform(get("/api/v1/platform-connection"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().json("{\"connected\":false}", JsonCompareMode.STRICT));
    }
}
