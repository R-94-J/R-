package cn.edu.campus.kb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiWorkflowTest {
    private HttpServer server;
    private AiClient client;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicReference<String> generated = new AtomicReference<>("图书馆22点关闭。[1]");
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicInteger embeddingCalls = new AtomicInteger();
    private final AtomicReference<JsonNode> lastChat = new AtomicReference<>();

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            lastChat.set(mapper.readTree(exchange.getRequestBody()));
            byte[] bytes = mapper.writeValueAsBytes(Map.of("choices", List.of(Map.of("message", Map.of("content", generated.get())))));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status.get(), bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.createContext("/v1/embeddings", exchange -> {
            embeddingCalls.incrementAndGet();
            JsonNode input = mapper.readTree(exchange.getRequestBody()).path("input");
            List<Map<String, Object>> data = new ArrayList<>();
            // Deliberately return indices in reverse order to verify batch reassembly.
            for (int i = input.size() - 1; i >= 0; i--) data.add(Map.of("index", i, "embedding", List.of(1.0, 0.0)));
            byte[] bytes = mapper.writeValueAsBytes(Map.of("data", data));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status.get(), bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        client = new AiClient("http://127.0.0.1:" + server.getAddress().getPort() + "/v1", "test-key", "test-chat", true, "test-embedding");
    }

    @AfterEach
    void stopServer() { server.stop(0); }

    @Test
    void realHttpGenerationPreservesCitationAndFallsBackOnInvalidOrFailedResponse() {
        var conversations = mock(ConversationRepository.class);
        var messages = mock(MessageRepository.class);
        when(messages.findByConversationIdOrderByCreatedAtAsc(anyString())).thenReturn(List.of());
        var retrieval = mock(RetrievalService.class);
        var source = new RetrievalService.Source(1, "doc-1", "图书馆指南", "学习资源", "chunk-1", 1, "图书馆每日22点关闭。", 1.0);
        when(retrieval.search(anyString())).thenReturn(new RetrievalService.SearchResult(List.of(source), "bm25", false));
        var policy = mock(QuestionPolicy.class);
        when(policy.decide(anyString(), anyList(), anyList())).thenReturn(new QuestionPolicy.Decision(true, true, "图书馆", "school", false));
        var service = new ChatService(conversations, messages, retrieval, client, mapper, policy, mock(OfficialWebsiteSearch.class));
        var result = service.ask("owner", null, "图书馆几点关闭？");
        assertThat(result.assistantMessage().mode()).isEqualTo("model");
        assertThat(result.assistantMessage().content()).contains("[1]");
        assertThat(lastChat.get().path("messages").path(0).path("content").asText()).contains("图书馆每日22点关闭");
        generated.set("图书馆23点关闭。[99]");
        var invalid = service.ask("owner", null, "图书馆几点关闭？");
        assertThat(invalid.assistantMessage().mode()).isEqualTo("fallback");
        assertThat(invalid.assistantMessage().content()).contains("22点").doesNotContain("23点");
        status.set(503);
        assertThat(service.ask("owner", null, "图书馆几点关闭？").assistantMessage().mode()).isEqualTo("fallback");
    }

    @Test
    void embeddingRequestsAreBatchedAndReordered() {
        List<double[]> vectors = client.embed(Collections.nCopies(17, "校园资料"));
        assertThat(embeddingCalls.get()).isEqualTo(2);
        assertThat(vectors).hasSize(17).allSatisfy(vector -> assertThat(vector).containsExactly(1.0, 0.0));
    }

    @Test
    void vectorRetrievalAndFailureFallbackKeepDocumentSources() {
        var docs = mock(DocumentRepository.class);
        var chunks = mock(ChunkRepository.class);
        KnowledgeDocument document = new KnowledgeDocument(); document.title = "图书馆指南"; document.category = "学习资源";
        KnowledgeChunk chunk = new KnowledgeChunk(); chunk.documentId = document.id; chunk.position = 1;
        chunk.content = "图书馆每日22点关闭。"; chunk.embedding = "[1.0,0.0]"; chunk.embeddingModel = "test-embedding";
        when(docs.findAll()).thenReturn(List.of(document)); when(chunks.findAll()).thenReturn(List.of(chunk));
        var retrieval = new RetrievalService(chunks, docs, client, mapper);
        var hybrid = retrieval.search("图书馆几点关门");
        assertThat(hybrid.method()).isEqualTo("hybrid");
        assertThat(hybrid.sources()).hasSize(1);
        assertThat(hybrid.sources().get(0).documentId()).isEqualTo(document.id);
        status.set(503);
        var fallback = retrieval.search("图书馆几点关门");
        assertThat(fallback.degraded()).isTrue();
        assertThat(fallback.method()).isEqualTo("bm25");
        assertThat(fallback.sources()).hasSize(1);
    }

    @Test
    void corruptStoredVectorDiscardsPartialVectorRanking() {
        var docs = mock(DocumentRepository.class);
        var chunks = mock(ChunkRepository.class);
        var canteen = new KnowledgeDocument(); canteen.title = "食堂指南"; canteen.category = "校园生活";
        var library = new KnowledgeDocument(); library.title = "图书馆指南"; library.category = "学习资源";
        var valid = new KnowledgeChunk(); valid.documentId = canteen.id; valid.content = "饭堂餐卡充值";
        valid.embedding = "[1.0,0.0]"; valid.embeddingModel = "test-embedding";
        var broken = new KnowledgeChunk(); broken.documentId = library.id; broken.content = "图书馆每日22点关闭";
        broken.embedding = "broken-json"; broken.embeddingModel = "test-embedding";
        when(docs.findAll()).thenReturn(List.of(canteen, library));
        when(chunks.findAll()).thenReturn(List.of(valid, broken));
        var result = new RetrievalService(chunks, docs, client, mapper).search("图书馆几点关闭");
        assertThat(result.degraded()).isTrue();
        assertThat(result.method()).isEqualTo("bm25");
        assertThat(result.sources()).extracting(RetrievalService.Source::documentId).containsExactly(library.id);
    }
}
