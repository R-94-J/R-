package cn.edu.campus.kb;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "kb_message", indexes = @Index(name = "idx_message_conversation", columnList = "conversationId"))
class ChatMessage {
    @Id public String id = UUID.randomUUID().toString();
    @Column(nullable = false) public String conversationId;
    public String role;
    @Lob @Column(nullable = false, length = 1_000_000) public String content;
    public String mode;
    @Lob @Column(length = 1_000_000) public String sourcesJson;
    public long latencyMs;
    public Integer feedback;
    public LocalDateTime createdAt = LocalDateTime.now();
}
