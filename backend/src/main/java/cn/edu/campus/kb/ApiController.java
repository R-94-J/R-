package cn.edu.campus.kb;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/api")
public class ApiController {
    public record Question(@NotBlank(message = "请输入问题") @Size(max = 600, message = "问题最多 600 字") String question,
                           @Size(max = 36, message = "会话编号格式不正确") String conversationId) {}
    public record Feedback(Integer value) {}
    public record ConversationTitle(@NotBlank(message = "请输入对话标题")
                                    @Size(max = 80, message = "对话标题最多 80 字") String title) {}
    public record RetrievalQuery(@NotBlank(message = "请输入检索问题")
                                 @Size(max = 600, message = "问题最多 600 字") String question) {}
    private final DocumentRepository documents;
    private final ChunkRepository chunks;
    private final MessageRepository messages;
    private final DocumentService documentService;
    private final ChatService chat;
    private final AiClient ai;
    private final StudentService students;
    private final StudentRepository studentAccounts;
    private final DocumentFileRepository files;
    private final RetrievalService retrieval;

    public ApiController(DocumentRepository documents, ChunkRepository chunks, MessageRepository messages,
                         DocumentService documentService, ChatService chat, AiClient ai,
                          StudentService students, StudentRepository studentAccounts, DocumentFileRepository files,
                          RetrievalService retrieval) {
        this.documents = documents; this.chunks = chunks; this.messages = messages;
        this.documentService = documentService; this.chat = chat; this.ai = ai;
        this.students = students; this.studentAccounts = studentAccounts; this.files = files;
        this.retrieval = retrieval;
    }

    @GetMapping("/auth/csrf")
    public Map<String, String> csrf(CsrfToken token) { return Map.of("token", token.getToken(), "headerName", token.getHeaderName()); }

    @GetMapping("/auth/me")
    public Map<String, Object> me(Authentication authentication) {
        boolean admin = authentication != null && authentication.isAuthenticated()
                && authentication.getAuthorities().stream().anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        boolean authenticated = authentication != null && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken);
        return Map.of("admin", admin, "authenticated", authenticated,
                "username", authenticated ? authentication.getName() : "学生访客");
    }

    @PostMapping("/auth/register")
    public org.springframework.http.ResponseEntity<?> register(@Valid @RequestBody StudentService.Registration request) {
        return org.springframework.http.ResponseEntity.status(201).body(Map.of("username", students.register(request), "message", "注册成功，请登录"));
    }

    @GetMapping("/files/{id}")
    public org.springframework.http.ResponseEntity<byte[]> download(@PathVariable String id) {
        KnowledgeDocument doc = documents.findById(id).orElseThrow(ApiException::notFound);
        DocumentFile file = files.findById(id).orElseThrow(ApiException::notFound);
        String disposition = org.springframework.http.ContentDisposition.attachment()
                .filename(doc.filename, java.nio.charset.StandardCharsets.UTF_8).build().toString();
        return org.springframework.http.ResponseEntity.ok()
                .header("Content-Disposition", disposition).header("X-Content-Type-Options", "nosniff")
                .contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(file.data.length).body(file.data);
    }

    @GetMapping("/chat/config")
    public Map<String, Object> chatConfig() {
        return Map.of("documentCount", documents.count(), "aiEnabled", ai.enabled());
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        long indexed = chunks.findAll().stream().filter(c -> c.embedding != null && ai.embeddingModel().equals(c.embeddingModel)).count();
        return Map.of("documentCount", documents.count(), "chunkCount", chunks.count(), "answerCount", messages.countByRole("assistant"),
                "helpfulCount", messages.countByFeedback(1), "aiEnabled", ai.enabled(), "model", ai.enabled() ? ai.model() : "本地检索",
                "embeddingEnabled", ai.embeddingsEnabled(), "indexedChunkCount", indexed, "maxUploadMb", 10);
    }

    @GetMapping("/documents")
    public List<KnowledgeDocument> documents() { return documents.findAllByOrderByCreatedAtDesc(); }

    /** 供管理员核对检索效果；不生成回答，也不写入学生对话历史。 */
    @PostMapping("/documents/search")
    public RetrievalService.SearchResult search(@Valid @RequestBody RetrievalQuery query) {
        return retrieval.search(query.question().strip());
    }

    @GetMapping("/documents/{id}")
    public Map<String, Object> document(@PathVariable String id) {
        return Map.of("document", documents.findById(id).orElseThrow(ApiException::notFound),
                "chunks", chunks.findByDocumentIdOrderByPositionAsc(id).stream()
                    .map(c -> Map.of("id", c.id, "position", c.position, "content", c.content,
                            "indexed", c.embedding != null && ai.embeddingModel().equals(c.embeddingModel))).toList());
    }

    @PostMapping("/documents")
    public KnowledgeDocument upload(@RequestParam MultipartFile file, @RequestParam(defaultValue = "") String title,
                                    @RequestParam(defaultValue = "其他资料") String category,
                                    @RequestParam(defaultValue = "") String sourceUrl,
                                    @RequestParam(defaultValue = "") String downloadUrl,
                                    @RequestParam(defaultValue = "") String publisher,
                                    @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate publishedDate,
                                    @RequestParam(defaultValue = "") String applicability) throws IOException {
        return documentService.uploadWithProvenance(file.getOriginalFilename(), file.getBytes(), title, category,
                new DocumentService.Provenance(sourceUrl, publisher, publishedDate, applicability, downloadUrl));
    }

    @PostMapping("/documents/{id}/reindex")
    public KnowledgeDocument reindex(@PathVariable String id) { return documentService.reindex(id); }

    @PostMapping("/documents/{id}/provenance")
    public KnowledgeDocument provenance(@PathVariable String id, @RequestBody DocumentService.Provenance source) {
        return documentService.provenance(id, source);
    }

    @PatchMapping("/documents/{id}")
    public KnowledgeDocument edit(@PathVariable String id, @RequestBody DocumentService.Edit edit) {
        return documentService.update(id, edit);
    }

    @DeleteMapping("/documents/{id}")
    public Map<String, String> deleteDocument(@PathVariable String id) {
        documentService.delete(id); return Map.of("message", "文档及其索引已删除");
    }

    @GetMapping("/conversations")
    public List<ChatService.ConversationView> conversations(HttpSession session) { return chat.conversations(owner(session)); }

    @GetMapping("/conversations/{id}/messages")
    public List<ChatService.MessageView> history(@PathVariable String id, HttpSession session) { return chat.history(id, owner(session)); }

    @PatchMapping("/conversations/{id}")
    public ChatService.ConversationView renameConversation(@PathVariable String id,
            @Valid @RequestBody ConversationTitle request, HttpSession session) {
        return chat.rename(id, owner(session), request.title());
    }

    @GetMapping("/conversations/{id}/export")
    public org.springframework.http.ResponseEntity<byte[]> exportConversation(@PathVariable String id, HttpSession session) {
        byte[] content = chat.export(id, owner(session)).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return org.springframework.http.ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=campus-conversation.md")
                .header("Cache-Control", "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .contentType(org.springframework.http.MediaType.parseMediaType("text/markdown;charset=UTF-8"))
                .contentLength(content.length).body(content);
    }

    @DeleteMapping("/conversations/{id}")
    public Map<String, String> deleteConversation(@PathVariable String id, HttpSession session) {
        chat.delete(id, owner(session)); return Map.of("message", "对话已删除");
    }

    @PostMapping("/chat")
    public ChatService.Answer ask(@Valid @RequestBody Question question, HttpSession session) {
        return chat.ask(owner(session), question.conversationId(), question.question());
    }

    @PostMapping("/messages/{id}/feedback")
    public ChatService.MessageView feedback(@PathVariable String id, @RequestBody Feedback feedback, HttpSession session) {
        return chat.feedback(id, owner(session), feedback.value());
    }

    private String owner(HttpSession session) {
        Authentication authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken)) {
            if (authentication.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())))
                return "admin:" + authentication.getName().toLowerCase(Locale.ROOT);
            return "student:" + studentAccounts.findByUsername(authentication.getName().toLowerCase(Locale.ROOT))
                    .orElseThrow(ApiException::notFound).id;
        }
        synchronized (session) {
            String owner = (String) session.getAttribute("chatOwner");
            if (owner == null) { owner = UUID.randomUUID().toString(); session.setAttribute("chatOwner", owner); }
            return owner;
        }
    }
}
