package cn.edu.campus.kb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.config.import=", "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:campus-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "app.seed-demo=false", "app.ai.api-key=", "app.website.enabled=false",
        "app.admin.username=admin", "app.admin.password=TestPassword!"
})
@AutoConfigureMockMvc
class KnowledgeApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired DocumentRepository documents;
    @Autowired ChunkRepository chunks;
    @Autowired ConversationRepository conversations;
    @Autowired MessageRepository messages;
    @Autowired DocumentService documentService;
    @Autowired DocumentFileRepository files;

    @BeforeEach
    void reset() {
        messages.deleteAll(); conversations.deleteAll(); chunks.deleteAll(); files.deleteAll(); documents.deleteAll();
    }

    private MockMultipartFile file() {
        return new MockMultipartFile("file", "图书馆.md", "text/markdown",
                "# 图书馆指南\n图书馆每天08:00开门，22:00关门。本科生可以借阅10册图书，借期30天。逾期图书需要先归还。".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void completeDocumentChatFeedbackAndOwnershipWorkflow() throws Exception {
        String docJson = mvc.perform(multipart("/api/documents").file(file()).param("title", "图书馆指南")
                .param("category", "学习资源").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.chunkCount").value(1)).andReturn().getResponse().getContentAsString();
        String docId = mapper.readTree(docJson).path("id").asText();
        mvc.perform(post("/api/documents/{id}/provenance", docId).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sourceUrl\":\"https://lib.sdyu.edu.cn/\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/documents/{id}/provenance", docId).with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"sourceUrl\":\"javascript:alert(1)\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/documents/{id}/provenance", docId).with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("sourceUrl", "https://lib.sdyu.edu.cn/", "publisher", "图书馆", "publishedDate", "2025-01-26", "applicability", "以最新通知为准"))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/documents/{id}", docId).with(user("admin").roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.chunks[0].content").value(org.hamcrest.Matchers.containsString("22:00")))
                .andExpect(jsonPath("$.chunks[0].embedding").doesNotExist());
        MockHttpSession owner = new MockHttpSession();
        JsonNode answer = mapper.readTree(mvc.perform(post("/api/chat").session(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("question", "图书馆几点关门？"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.assistantMessage.mode").value("local"))
                .andExpect(jsonPath("$.assistantMessage.sources[0].documentId").value(docId))
                .andExpect(jsonPath("$.assistantMessage.sources[0].sourceUrl").value("https://lib.sdyu.edu.cn/"))
                .andExpect(jsonPath("$.assistantMessage.sources[0].publishedDate").value("2025-01-26"))
                .andExpect(jsonPath("$.assistantMessage.content").value(org.hamcrest.Matchers.containsString("22:00")))
                .andReturn().getResponse().getContentAsString());
        String conversationId = answer.path("conversationId").asText();
        String messageId = answer.path("assistantMessage").path("id").asText();
        mvc.perform(get("/api/conversations/{id}/messages", conversationId).session(owner)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/conversations/{id}/messages", conversationId).session(new MockHttpSession())).andExpect(status().isNotFound());
        mvc.perform(post("/api/messages/{id}/feedback", messageId).session(new MockHttpSession()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"value\":1}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/messages/{id}/feedback", messageId).session(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"value\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.feedback").value(1));
        mvc.perform(post("/api/messages/{id}/feedback", messageId).session(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"value\":5}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/status").with(user("admin").roles("ADMIN"))).andExpect(jsonPath("$.helpfulCount").value(1));
        mvc.perform(delete("/api/documents/{id}", docId).with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isOk());
        assertThat(chunks.count()).isZero();
        mvc.perform(get("/api/conversations/{id}/messages", conversationId).session(owner))
                .andExpect(jsonPath("$[1].sources[0].excerpt").value(org.hamcrest.Matchers.containsString("22:00")));
        mvc.perform(post("/api/chat").session(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"图书馆几点关门\"}"))
                .andExpect(jsonPath("$.assistantMessage.mode").value("insufficient"));
        mvc.perform(delete("/api/conversations/{id}", conversationId).session(owner).with(csrf())).andExpect(status().isOk());
        assertThat(messages.findByConversationIdOrderByCreatedAtAsc(conversationId)).isEmpty();
    }

    @Test
    void rejectsUnauthorizedCsrfDuplicateInvalidQuestionAndUnrelatedQuery() throws Exception {
        mvc.perform(multipart("/api/documents").file(file()).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/documents").file(file()).with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(multipart("/api/documents").file(file()).with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isOk());
        mvc.perform(multipart("/api/documents").file(file()).with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isConflict());
        mvc.perform(post("/api/chat").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"   \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/chat").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("question", "字".repeat(601)))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/chat").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"量子纠缠的物理原理是什么\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.assistantMessage.mode").value("insufficient"));
    }

    @Test
    void loginUsesRealSessionAndRotatingCsrfToken() throws Exception {
        MockHttpSession session = new MockHttpSession();
        JsonNode csrf = mapper.readTree(mvc.perform(get("/api/auth/csrf").session(session)).andReturn().getResponse().getContentAsString());
        mvc.perform(post("/api/auth/login").session(session).header(csrf.path("headerName").asText(), csrf.path("token").asText())
                .param("username", "admin").param("password", "TestPassword!"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").session(session)).andExpect(jsonPath("$.admin").value(true));
        JsonNode fresh = mapper.readTree(mvc.perform(get("/api/auth/csrf").session(session)).andReturn().getResponse().getContentAsString());
        mvc.perform(multipart("/api/documents").file(file()).session(session)
                .header(fresh.path("headerName").asText(), fresh.path("token").asText())).andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").session(session).header(fresh.path("headerName").asText(), fresh.path("token").asText()))
                .andExpect(status().isNoContent());
    }

    @Test
    void longChineseTextCanBeStoredAndRead() {
        String text = "图书馆预约须遵循相关规定，每人每天可预约四小时。\n".repeat(200);
        var doc = documentService.upload("long.txt", text.getBytes(StandardCharsets.UTF_8), "长文档", "学习资源", false);
        assertThat(doc.characterCount).isGreaterThan(4000);
        assertThat(chunks.findByDocumentIdOrderByPositionAsc(doc.id)).hasSizeGreaterThan(5);
    }

    @Test
    void studentsCannotAccessManagementEvenWithDirectApiRequests() throws Exception {
        for (String path : java.util.List.of("/api/documents", "/api/documents/not-a-document", "/api/status")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).with(user("student").roles("STUDENT"))).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/auth/me").with(user("student").roles("STUDENT")))
                .andExpect(jsonPath("$.admin").value(false));
        mvc.perform(get("/api/chat/config")).andExpect(status().isOk())
                .andExpect(jsonPath("$.documentCount").exists()).andExpect(jsonPath("$.model").doesNotExist())
                .andExpect(jsonPath("$.answerCount").doesNotExist());
        mvc.perform(get("/api/documents").with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
    }

    @Test
    void adminEditsMetadataWhileChunksAndAnsweredSnapshotsStayUnchanged() throws Exception {
        String docId = mapper.readTree(mvc.perform(multipart("/api/documents").file(file()).param("title", "图书馆指南")
                        .param("category", "学习资源").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("id").asText();
        MockHttpSession owner = new MockHttpSession();
        String before = mvc.perform(post("/api/chat").session(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"图书馆几点关门？\"}"))
                .andExpect(jsonPath("$.assistantMessage.sources[0].title").value("图书馆指南")).andReturn().getResponse().getContentAsString();
        String conversationId = mapper.readTree(before).path("conversationId").asText();
        var originalDocument = documents.findById(docId).orElseThrow();
        String originalHash = originalDocument.contentHash;
        String originalChunkId = chunks.findByDocumentIdOrderByPositionAsc(docId).get(0).id;
        mvc.perform(post("/api/documents/{id}/provenance", docId).with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"downloadUrl\":\"https://lib.sdyu.edu.cn/guide.docx\"}"))
                .andExpect(status().isOk());
        String edit = mapper.writeValueAsString(Map.of("title", "图书馆开放时间指南（2025 修订）", "category", "校园生活",
                "sourceUrl", "https://lib.sdyu.edu.cn/info/1001/1.htm", "publisher", "山东青年政治学院图书馆",
                "publishedDate", "2025-03-01", "applicability", "自2025年3月起执行，以最新通知为准"));
        mvc.perform(patch("/api/documents/{id}", docId).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(edit)).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/documents/{id}", docId).with(user("student").roles("STUDENT")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(edit)).andExpect(status().isForbidden());
        mvc.perform(patch("/api/documents/{id}", docId).with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(edit)).andExpect(status().isForbidden());
        mvc.perform(patch("/api/documents/{id}", docId).with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(edit))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("图书馆开放时间指南（2025 修订）"))
                .andExpect(jsonPath("$.category").value("校园生活"))
                .andExpect(jsonPath("$.publishedDate").value("2025-03-01"))
                // Clearing an external link must fall back to the stored original instead of losing the download entry.
                .andExpect(jsonPath("$.downloadUrl").value("/api/files/" + docId));
        mvc.perform(patch("/api/documents/{id}", docId).with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"  \"}")).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/documents/{id}", docId).with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"合法标题\",\"sourceUrl\":\"javascript:alert(1)\"}")).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/documents/{id}", docId).with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("title", "合法标题", "publisher", "单".repeat(101)))))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/documents/missing-document").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(edit)).andExpect(status().isNotFound());
        assertThat(documents.findById(docId).orElseThrow().title).isEqualTo("图书馆开放时间指南（2025 修订）");
        assertThat(documents.findById(docId).orElseThrow().contentHash).isEqualTo(originalHash);
        assertThat(chunks.findByDocumentIdOrderByPositionAsc(docId)).singleElement()
                .satisfies(chunk -> {
                    assertThat(chunk.id).isEqualTo(originalChunkId);
                    assertThat(chunk.content).contains("22:00");
                });
        mvc.perform(get("/api/files/{id}", docId)).andExpect(status().isOk()).andExpect(content().bytes(file().getBytes()));
        mvc.perform(get("/api/conversations/{id}/messages", conversationId).session(owner))
                .andExpect(jsonPath("$[1].sources[0].title").value("图书馆指南"))
                .andExpect(jsonPath("$[1].sources[0].sourceUrl").doesNotExist());
        mvc.perform(post("/api/chat").session(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("question", "图书馆几点关门？", "conversationId", conversationId))))
                .andExpect(jsonPath("$.assistantMessage.sources[0].title").value("图书馆开放时间指南（2025 修订）"))
                .andExpect(jsonPath("$.assistantMessage.sources[0].sourceUrl").value("https://lib.sdyu.edu.cn/info/1001/1.htm"))
                .andExpect(jsonPath("$.assistantMessage.sources[0].applicability").value("自2025年3月起执行，以最新通知为准"));
    }

    @Test
    void uploadSavesCompleteProvenanceAndRejectsInvalidMetadataWithoutSavingFiles() throws Exception {
        mvc.perform(multipart("/api/documents").file(file()).param("publisher", "单".repeat(101))
                .with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/documents").file(file()).param("publishedDate", "not-a-date")
                .with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isBadRequest());
        assertThat(documents.count()).isZero();
        assertThat(chunks.count()).isZero();
        assertThat(files.count()).isZero();
        mvc.perform(multipart("/api/documents").file(file()).param("title", "图书馆指南")
                        .param("sourceUrl", "https://lib.sdyu.edu.cn/").param("publisher", "  图书馆  ")
                        .param("publishedDate", "2025-03-01").param("applicability", "  适用于全体在校生  ")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.publisher").value("图书馆"))
                .andExpect(jsonPath("$.publishedDate").value("2025-03-01"))
                .andExpect(jsonPath("$.applicability").value("适用于全体在校生"));
    }

    @Test
    void retrievalPreviewIsAdminOnlyAndDoesNotCreateChatMessages() throws Exception {
        var doc = documentService.upload("图书馆.md", file().getBytes(), "图书馆指南", "学习资源", false);
        String body = "{\"question\":\"图书馆几点关门\"}";
        mvc.perform(post("/api/documents/search").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/documents/search").with(csrf()).with(user("student").roles("STUDENT"))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/documents/search").with(csrf()).with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.method").value("bm25"))
                .andExpect(jsonPath("$.sources[0].documentId").value(doc.id))
                .andExpect(jsonPath("$.sources[0].excerpt").value(org.hamcrest.Matchers.containsString("22:00")));
        assertThat(messages.count()).isZero();
        assertThat(conversations.count()).isZero();
    }

    @Test
    void conversationRenameAndExportEnforceOwnershipAndKeepHistoricalSources() throws Exception {
        var doc = documentService.upload("library.md", file().getBytes(), "图书馆旧指南", "学习资源", false);
        documentService.provenance(doc.id, new DocumentService.Provenance("https://lib.sdyu.edu.cn/old.htm",
                "图书馆", java.time.LocalDate.of(2025, 1, 1), "2025版", null));
        var owner = new MockHttpSession();
        JsonNode answer = mapper.readTree(mvc.perform(post("/api/chat").session(owner).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"图书馆几点关门？\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String id = answer.path("conversationId").asText();
        var updatedAt = conversations.findById(id).orElseThrow().updatedAt;
        String rename = "{\"title\":\"  我的图书馆笔记  \"}";
        mvc.perform(patch("/api/conversations/{id}", id).session(new MockHttpSession()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(rename)).andExpect(status().isNotFound());
        mvc.perform(patch("/api/conversations/{id}", id).session(owner)
                .contentType(MediaType.APPLICATION_JSON).content(rename)).andExpect(status().isForbidden());
        mvc.perform(patch("/api/conversations/{id}", id).session(owner).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(rename)).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("我的图书馆笔记"));
        assertThat(conversations.findById(id).orElseThrow().updatedAt).isEqualTo(updatedAt);
        mvc.perform(get("/api/conversations").session(owner)).andExpect(jsonPath("$[0].title").value("我的图书馆笔记"));
        for (String title : java.util.List.of("   ", "字".repeat(81), "标题\n伪造下一行")) {
            mvc.perform(patch("/api/conversations/{id}", id).session(owner).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("title", title))))
                    .andExpect(status().isBadRequest());
        }
        documentService.update(doc.id, new DocumentService.Edit("全新标题", "校园生活", "https://lib.sdyu.edu.cn/new.htm",
                "新单位", null, "新版本", "https://lib.sdyu.edu.cn/new.docx"));
        documentService.delete(doc.id);
        String exported = mvc.perform(get("/api/conversations/{id}/export", id).session(owner))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(content().contentTypeCompatibleWith("text/markdown"))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(exported).contains("我的图书馆笔记", "图书馆旧指南", "2025版", "https://lib.sdyu.edu.cn/old.htm", "22:00", "知识库原文", "/api/files/" + doc.id)
                .doesNotContain("全新标题", "https://lib.sdyu.edu.cn/new.htm", "owner_key");
        mvc.perform(get("/api/conversations/{id}/export", id).session(new MockHttpSession())).andExpect(status().isNotFound());
        assertThat(messages.count()).isEqualTo(2);
        mvc.perform(delete("/api/conversations/{id}", id).session(owner).with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/conversations/{id}/export", id).session(owner)).andExpect(status().isNotFound());
    }
}
