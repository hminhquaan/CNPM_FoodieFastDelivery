import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = AppMain.class)
@AutoConfigureMockMvc
@ActiveProfiles("dev")
public class AdminDiagnosticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void summary_shouldReturnOk() throws Exception {
        mockMvc.perform(get("/api/admin/diagnostics/summary"))
                .andExpect(status().isOk());
    }

    @Test
    void normalizeCategories_execute_shouldReturnOk() throws Exception {
        mockMvc.perform(post("/api/admin/categories/normalize").param("dryRun", "false"))
                .andExpect(status().isOk());
    }
}
