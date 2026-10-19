package cn.edu.campus.kb;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** 文档元数据。正文保存在片段表，上传原件保存在文件表。 */
@Entity
@Table(name = "kb_document")
class KnowledgeDocument {
    @Id public String id = UUID.randomUUID().toString();
    @Column(nullable = false, length = 120) public String title;
    @Column(nullable = false, length = 40) public String category;
    @Column(length = 255) public String filename;
    public String fileType;
    public long fileSize;
    public int characterCount;
    public int chunkCount;
    public boolean demo;
    @Column(length = 1000) public String sourceUrl;
    @Column(length = 1000) public String downloadUrl;
    @Column(length = 100) public String publisher;
    public LocalDate publishedDate;
    @Column(length = 500) public String applicability;
    @Column(unique = true, nullable = false, length = 64) public String contentHash;
    public LocalDateTime createdAt = LocalDateTime.now();
}
