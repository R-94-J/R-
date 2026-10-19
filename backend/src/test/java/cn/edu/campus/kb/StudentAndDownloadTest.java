package cn.edu.campus.kb;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.config.import=", "spring.profiles.active=test", "spring.datasource.url=jdbc:h2:mem:accounts;DB_CLOSE_DELAY=-1", "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop", "app.seed-demo=false", "app.ai.api-key=", "app.website.enabled=false", "app.admin.username=admin", "app.admin.password=TestPassword!"})
@AutoConfigureMockMvc
class StudentAndDownloadTest {
    @Autowired MockMvc mvc;
    @Autowired StudentRepository students;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper mapper;

    @Test
    void adminCanLoginRepeatedlyAfterCredentialsAreErasedBySpring() throws Exception {
        for (int i = 0; i < 3; i++) {
            var session = new MockHttpSession();
            mvc.perform(post("/api/auth/login").session(session).with(csrf()).param("username", "admin").param("password", "TestPassword!"))
                    .andExpect(status().isOk());
            mvc.perform(get("/api/documents").session(session)).andExpect(status().isOk());
        }
    }

    @Test
    void registrationStoresHashAndHistorySurvivesNewLoginButIsIsolated() throws Exception {
        String body = "{\"username\":\"Learner_01\",\"password\":\"Password123!\",\"role\":\"ADMIN\"}";
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(body)).andExpect(status().isCreated());
        var student = students.findByUsername("learner_01").orElseThrow();
        assertThat(student.passwordHash).isNotEqualTo("Password123!");
        assertThat(encoder.matches("Password123!", student.passwordHash)).isTrue();
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(body)).andExpect(status().isConflict());
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content("{\"username\":\"admin\",\"password\":\"Password123!\"}")).andExpect(status().isConflict());
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content("{\"username\":\"validname\",\"password\":\"short\"}")).andExpect(status().isBadRequest());
        var first = new MockHttpSession();
        mvc.perform(post("/api/auth/login").session(first).with(csrf()).param("username", "LEARNER_01").param("password", "Password123!")).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").session(first)).andExpect(jsonPath("$.authenticated").value(true)).andExpect(jsonPath("$.admin").value(false));
        mvc.perform(get("/api/documents").session(first)).andExpect(status().isForbidden());
        var response = mvc.perform(post("/api/chat").session(first).with(csrf()).contentType("application/json").content("{\"question\":\"校园图书馆在哪里\"}")).andExpect(status().isOk()).andReturn();
        String id = mapper.readTree(response.getResponse().getContentAsString()).path("conversationId").asText();
        mvc.perform(post("/api/auth/logout").session(first).with(csrf())).andExpect(status().isNoContent());
        var second = new MockHttpSession();
        mvc.perform(post("/api/auth/login").session(second).with(csrf()).param("username", "learner_01").param("password", "wrong")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").session(second).with(csrf()).param("username", "learner_01").param("password", "Password123!")).andExpect(status().isOk());
        mvc.perform(get("/api/conversations/{id}/messages", id).session(second)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/conversations/{id}/messages", id).session(new MockHttpSession())).andExpect(status().isNotFound());
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content("{\"username\":\"learner_02\",\"password\":\"Password123!\"}")).andExpect(status().isCreated());
        mvc.perform(get("/api/conversations/{id}/messages", id).with(user("learner_02").roles("STUDENT"))).andExpect(status().isNotFound());
    }

    @Test
    void originalFileAndExternalDownloadSurviveInAnswerSnapshots() throws Exception {
        byte[] bytes = "创新项目申请表：填写项目名称、负责人姓名、指导教师和项目计划，提交学院审核。".getBytes(StandardCharsets.UTF_8);
        var file = new MockMultipartFile("file", "申请表.txt", "text/plain", bytes);
        var uploaded = mvc.perform(multipart("/api/documents").file(file).param("title", "创新项目申请表").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk()).andReturn();
        var doc = mapper.readTree(uploaded.getResponse().getContentAsString());
        String id = doc.path("id").asText();
        mvc.perform(get("/api/files/{id}", id)).andExpect(status().isOk()).andExpect(content().bytes(bytes)).andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")));
        String link = "https://jwc.sdyu.edu.cn/system/_content/download.jsp?wbfileid=16787504&owner=1580020116";
        mvc.perform(post("/api/documents/{id}/provenance", id).with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("downloadUrl", link)))).andExpect(status().isOk());
        var session = new MockHttpSession();
        var result = mvc.perform(post("/api/chat").session(session).with(csrf()).contentType("application/json").content("{\"question\":\"创新项目申请表怎么下载？\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.assistantMessage.sources[0].downloadUrl").value(link)).andReturn();
        String conversation = mapper.readTree(result.getResponse().getContentAsString()).path("conversationId").asText();
        mvc.perform(get("/api/conversations/{id}/messages", conversation).session(session)).andExpect(jsonPath("$[1].sources[0].downloadUrl").value(link));
        mvc.perform(delete("/api/documents/{id}", id).with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/files/{id}", id)).andExpect(status().isNotFound());
        mvc.perform(multipart("/api/documents").file(file).param("downloadUrl", "javascript:alert(1)").with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isBadRequest());
    }
}
