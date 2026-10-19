package cn.edu.campus.kb;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Service
public class DocumentService {
    private final DocumentRepository documents;
    private final ChunkRepository chunks;
    private final DocumentParser parser;
    private final AiClient ai;
    private final ObjectMapper mapper;
    private final DocumentFileRepository files;

    public DocumentService(DocumentRepository documents, ChunkRepository chunks, DocumentParser parser, AiClient ai, ObjectMapper mapper, DocumentFileRepository files) {
        this.documents = documents; this.chunks = chunks; this.parser = parser; this.ai = ai; this.mapper = mapper;
        this.files = files;
    }

    @Transactional
    public KnowledgeDocument upload(String filename, byte[] bytes, String title, String category, boolean demo) {
        if (filename == null) throw ApiException.badRequest("文件名不能为空");
        filename = filename.replace('\\', '/');
        filename = filename.substring(filename.lastIndexOf('/') + 1);
        if (filename.length() > 255) throw ApiException.badRequest("文件名过长");
        title = title == null || title.isBlank() ? filename : title.strip();
        category = category == null || category.isBlank() ? "其他资料" : category.strip();
        if (title.length() > 120 || category.length() > 40) throw ApiException.badRequest("标题最多 120 字，分类最多 40 字");
        String text = parser.parse(filename, bytes);
        String hash;
        try { hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
        if (documents.existsByContentHash(hash)) throw new ApiException(HttpStatus.CONFLICT, "这份资料已存在，请勿重复上传");
        if (documents.count() >= 200) throw ApiException.badRequest("当前版本最多支持 200 份文档，请先清理旧资料");
        List<String> parts = parser.split(text);
        List<double[]> embeddings = List.of();
        if (ai.embeddingsEnabled() && !demo) {
            try { embeddings = ai.embed(parts); }
            catch (Exception ex) { throw new ApiException(HttpStatus.BAD_GATEWAY, "向量接口调用失败，资料未保存；请检查模型配置或关闭向量模式后重试"); }
        }
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.title = title; doc.category = category; doc.filename = filename;
        doc.fileType = parser.extension(filename); doc.fileSize = bytes.length;
        doc.characterCount = text.length(); doc.chunkCount = parts.size(); doc.contentHash = hash; doc.demo = demo;
        documents.save(doc);
        DocumentFile file = new DocumentFile(); file.id = doc.id; file.data = bytes.clone(); files.save(file);
        doc.downloadUrl = "/api/files/" + doc.id;
        List<KnowledgeChunk> records = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            KnowledgeChunk chunk = new KnowledgeChunk();
            chunk.documentId = doc.id; chunk.position = i + 1; chunk.content = parts.get(i);
            if (!embeddings.isEmpty()) {
                try { chunk.embedding = mapper.writeValueAsString(embeddings.get(i)); }
                catch (Exception ex) { throw new IllegalStateException(ex); }
                chunk.embeddingModel = ai.embeddingModel();
            }
            records.add(chunk);
        }
        chunks.saveAll(records);
        return documents.save(doc);
    }

    @Transactional
    public KnowledgeDocument reindex(String id) {
        KnowledgeDocument document = documents.findById(id).orElseThrow(ApiException::notFound);
        if (!ai.embeddingsEnabled()) throw ApiException.badRequest("请先配置并启用向量模型");
        List<KnowledgeChunk> records = chunks.findByDocumentIdOrderByPositionAsc(id);
        try {
            List<double[]> vectors = ai.embed(records.stream().map(chunk -> chunk.content).toList());
            for (int i = 0; i < records.size(); i++) {
                records.get(i).embedding = mapper.writeValueAsString(vectors.get(i));
                records.get(i).embeddingModel = ai.embeddingModel();
            }
            chunks.saveAll(records);
        } catch (Exception ex) { throw new ApiException(HttpStatus.BAD_GATEWAY, "向量索引更新失败，请检查接口配置后重试"); }
        return document;
    }

    public record Provenance(String sourceUrl, String publisher, java.time.LocalDate publishedDate, String applicability, String downloadUrl) {}

    /** Editable metadata of an archived document; the parsed text, chunks and original file stay untouched. */
    public record Edit(String title, String category, String sourceUrl, String publisher,
                       java.time.LocalDate publishedDate, String applicability, String downloadUrl) {}

    @Transactional
    public KnowledgeDocument uploadWithProvenance(String filename, byte[] bytes, String title, String category, Provenance source) {
        String url = checkedUrl(source.sourceUrl(), "官网来源"), download = checkedUrl(source.downloadUrl(), "原文件下载链接");
        String publisher = checkedText(source.publisher(), 100, "发布单位");
        String applicability = checkedText(source.applicability(), 500, "适用说明");
        KnowledgeDocument doc = upload(filename, bytes, title, category, false);
        doc.sourceUrl = url;
        doc.publisher = publisher;
        doc.publishedDate = source.publishedDate();
        doc.applicability = applicability;
        if (download != null) doc.downloadUrl = download;
        return documents.save(doc);
    }

    private String checkedUrl(String value, String label) {
        if (value == null || value.isBlank()) return null;
        String url = value.strip();
        try {
            var parsed = java.net.URI.create(url);
            if (!("https".equalsIgnoreCase(parsed.getScheme()) || "http".equalsIgnoreCase(parsed.getScheme()))
                    || parsed.getHost() == null || parsed.getUserInfo() != null || url.length() > 1000)
                throw new IllegalArgumentException();
        } catch (IllegalArgumentException ex) { throw ApiException.badRequest(label + "必须是有效的 HTTP/HTTPS 网页地址"); }
        return url;
    }

    /** Trim an optional text field, enforce its limit and treat blank input as "cleared". */
    private String checkedText(String value, int max, String label) {
        if (value == null) return null;
        String text = value.strip();
        if (text.isEmpty()) return null;
        if (text.length() > max) throw ApiException.badRequest(label + "最多 " + max + " 字");
        return text;
    }

    @Transactional
    public KnowledgeDocument provenance(String id, Provenance source) {
        KnowledgeDocument document = documents.findById(id).orElseThrow(ApiException::notFound);
        document.sourceUrl = checkedUrl(source.sourceUrl(), "来源");
        document.publisher = checkedText(source.publisher(), 100, "发布单位");
        document.publishedDate = source.publishedDate();
        document.applicability = checkedText(source.applicability(), 500, "适用说明");
        if (source.downloadUrl() != null) document.downloadUrl = localFileFallback(id, checkedUrl(source.downloadUrl(), "下载链接"));
        return documents.save(document);
    }

    /** Keep the stored original reachable when an administrator clears an external download link. */
    private String localFileFallback(String id, String download) {
        return download == null && files.existsById(id) ? "/api/files/" + id : download;
    }

    @Transactional
    public KnowledgeDocument update(String id, Edit edit) {
        KnowledgeDocument document = documents.findById(id).orElseThrow(ApiException::notFound);
        String title = checkedText(edit.title(), 120, "文档标题");
        if (title == null) throw ApiException.badRequest("请填写文档标题");
        String category = checkedText(edit.category(), 40, "资料分类");
        document.title = title;
        document.category = category == null ? "其他资料" : category;
        document.sourceUrl = checkedUrl(edit.sourceUrl(), "官网来源");
        document.publisher = checkedText(edit.publisher(), 100, "发布单位");
        document.publishedDate = edit.publishedDate();
        document.applicability = checkedText(edit.applicability(), 500, "适用说明");
        document.downloadUrl = localFileFallback(id, checkedUrl(edit.downloadUrl(), "原文件下载链接"));
        return documents.save(document);
    }

    @Transactional
    public void delete(String id) {
        KnowledgeDocument document = documents.findById(id).orElseThrow(ApiException::notFound);
        chunks.deleteByDocumentId(id);
        files.deleteById(id);
        documents.delete(document);
    }
}
