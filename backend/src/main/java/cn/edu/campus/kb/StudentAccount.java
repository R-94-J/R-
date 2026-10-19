package cn.edu.campus.kb;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "kb_student")
class StudentAccount {
    @Id public String id = UUID.randomUUID().toString();
    @Column(nullable = false, unique = true, length = 32) public String username;
    @JsonIgnore
    @Column(nullable = false, length = 100) public String passwordHash;
    public LocalDateTime createdAt = LocalDateTime.now();
}
