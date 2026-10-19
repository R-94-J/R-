package cn.edu.campus.kb;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class RetrievalService {
    public record Source(int index, String documentId, String title, String category,
                         String chunkId, int position, String excerpt, double score,
                         String sourceUrl, String publisher, String publishedDate, String applicability, String downloadUrl) {
        public Source(int index, String documentId, String title, String category, String chunkId, int position, String excerpt, double score) {
            this(index, documentId, title, category, chunkId, position, excerpt, score, null, null, null, null, null);
        }
    }
    public record SearchResult(List<Source> sources, String method, boolean degraded) {}
    private record Candidate(KnowledgeChunk chunk, double score) {}
    private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);
    private static final Pattern TOKENS = Pattern.compile("[\\p{IsHan}]+|[a-z0-9]+", Pattern.CASE_INSENSITIVE);
    private static final Set<String> STOP = Set.of("怎么", "如何", "什么", "可以", "是否", "请问", "一下", "的", "了", "吗", "呢", "我", "是", "在");
    private final ChunkRepository chunks;
    private final DocumentRepository documents;
    private final AiClient ai;
    private final ObjectMapper mapper;

    public RetrievalService(ChunkRepository chunks, DocumentRepository documents, AiClient ai, ObjectMapper mapper) {
        this.chunks = chunks; this.documents = documents; this.ai = ai; this.mapper = mapper;
    }

    static List<String> tokenize(String text) {
        List<String> terms = new ArrayList<>();
        var matcher = TOKENS.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String word = matcher.group();
            if (Character.UnicodeScript.of(word.codePointAt(0)) == Character.UnicodeScript.HAN) {
                int[] points = word.codePoints().toArray();
                if (points.length == 1) terms.add(word);
                for (int i = 0; i < points.length - 1; i++) terms.add(new String(points, i, 2));
            } else terms.add(word);
        }
        terms.removeIf(STOP::contains);
        return terms;
    }

    public SearchResult search(String query) {
        List<KnowledgeChunk> all = chunks.findAll();
        Map<String, KnowledgeDocument> docs = documents.findAll().stream().collect(Collectors.toMap(doc -> doc.id, doc -> doc));
        all = all.stream().filter(chunk -> docs.containsKey(chunk.documentId)).toList();
        if (all.isEmpty()) return new SearchResult(List.of(), "bm25", false);
        Set<String> queryTerms = new HashSet<>(tokenize(query));
        List<Map<String, Integer>> frequencies = new ArrayList<>();
        Map<String, Integer> documentFrequency = new HashMap<>();
        List<Integer> lengths = new ArrayList<>();
        for (KnowledgeChunk chunk : all) {
            // Include title in the searchable text, so short topic questions can find the right guide.
            List<String> terms = tokenize(docs.get(chunk.documentId).title + "\n" + chunk.content);
            Map<String, Integer> tf = new HashMap<>();
            terms.forEach(term -> tf.merge(term, 1, Integer::sum));
            tf.keySet().forEach(term -> documentFrequency.merge(term, 1, Integer::sum));
            frequencies.add(tf); lengths.add(terms.size());
        }
        double averageLength = lengths.stream().mapToInt(Integer::intValue).average().orElse(1);
        List<Candidate> lexical = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            double score = 0;
            int matches = 0;
            for (String term : queryTerms) {
                int tf = frequencies.get(i).getOrDefault(term, 0);
                if (tf == 0) continue;
                matches++;
                int df = documentFrequency.getOrDefault(term, 0);
                double idf = Math.log(1 + (all.size() - df + 0.5) / (df + 0.5));
                score += idf * tf * 2.2 / (tf + 1.2 * (0.25 + 0.75 * lengths.get(i) / Math.max(1, averageLength)));
            }
            int requiredMatches = queryTerms.size() > 3 ? 2 : 1;
            if (matches >= requiredMatches && score > 0) lexical.add(new Candidate(all.get(i), score));
        }
        lexical.sort(Comparator.comparingDouble(Candidate::score).reversed());
        List<Candidate> vector = new ArrayList<>();
        boolean degraded = false;
        boolean vectorAttempted = ai.embeddingsEnabled() && all.stream().anyMatch(c -> c.embedding != null && ai.embeddingModel().equals(c.embeddingModel));
        if (vectorAttempted) {
            try {
                double[] queryVector = ai.embed(List.of(query)).get(0);
                for (KnowledgeChunk chunk : all) {
                    if (chunk.embedding == null || !ai.embeddingModel().equals(chunk.embeddingModel)) continue;
                    double similarity = cosine(queryVector, mapper.readValue(chunk.embedding, double[].class));
                    if (similarity >= 0.55) vector.add(new Candidate(chunk, similarity));
                }
                vector.sort(Comparator.comparingDouble(Candidate::score).reversed());
            } catch (Exception ex) {
                // 已读到的向量候选也必须丢弃，降级后只保留完整的BM25结果。
                vector.clear();
                degraded = true;
                log.warn("Embedding search unavailable; using BM25 ({})", ex.getClass().getSimpleName());
            }
        }
        List<Candidate> ranked = lexical;
        if (!vector.isEmpty()) {
            Map<String, Double> fused = new HashMap<>();
            Map<String, KnowledgeChunk> byId = new HashMap<>();
            for (List<Candidate> group : List.of(lexical, vector)) {
                for (int i = 0; i < Math.min(group.size(), 20); i++) {
                    Candidate item = group.get(i);
                    byId.put(item.chunk.id, item.chunk);
                    fused.merge(item.chunk.id, 1.0 / (60 + i + 1), Double::sum);
                }
            }
            ranked = fused.entrySet().stream().map(entry -> new Candidate(byId.get(entry.getKey()), entry.getValue()))
                    .sorted(Comparator.comparingDouble(Candidate::score).reversed()).toList();
        }
        List<Source> sources = new ArrayList<>();
        Map<String, Integer> perDocument = new HashMap<>();
        for (Candidate item : ranked) {
            KnowledgeDocument doc = docs.get(item.chunk.documentId);
            if (perDocument.getOrDefault(doc.id, 0) >= 2) continue;
            perDocument.merge(doc.id, 1, Integer::sum);
            sources.add(new Source(sources.size() + 1, doc.id, doc.title, doc.category, item.chunk.id,
                    item.chunk.position, item.chunk.content, Math.round(item.score * 10000.0) / 10000.0,
                    doc.sourceUrl, doc.publisher, doc.publishedDate == null ? null : doc.publishedDate.toString(), doc.applicability, doc.downloadUrl));
            if (sources.size() == 5) break;
        }
        return new SearchResult(sources, !vector.isEmpty() ? "hybrid" : "bm25", degraded);
    }

    static double cosine(double[] a, double[] b) {
        if (a.length != b.length || a.length == 0) return 0;
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) { dot += a[i] * b[i]; normA += a[i] * a[i]; normB += b[i] * b[i]; }
        return normA == 0 || normB == 0 ? 0 : dot / Math.sqrt(normA * normB);
    }
}
