package cn.edu.campus.kb;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "kb_document_file")
class DocumentFile {
    @Id public String id;
    @Lob @Column(nullable = false, length = 16_777_215) public byte[] data;
}
