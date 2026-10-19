package cn.edu.campus.kb;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "kb_conversation", indexes = @Index(name = "idx_conversation_owner", columnList = "ownerKey"))
class Conversation {
    @Id public String id = UUID.randomUUID().toString();
    @Column(nullable = false) public String ownerKey;
    @Column(length = 80) public String title;
    public LocalDateTime createdAt = LocalDateTime.now();
    public LocalDateTime updatedAt = LocalDateTime.now();
}
