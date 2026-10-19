package cn.edu.campus.kb;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "kb_chunk", indexes = @Index(name = "idx_chunk_document", columnList = "documentId"))
class KnowledgeChunk {
    @Id public String id = UUID.randomUUID().toString();
    @Column(nullable = false) public String documentId;
    public int position;
    @Lob @Column(nullable = false, length = 1_000_000) public String content;
    @Lob @Column(length = 1_000_000) public String embedding;
    public String embeddingModel;
}
