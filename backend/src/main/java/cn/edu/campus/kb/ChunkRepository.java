package cn.edu.campus.kb;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

interface ChunkRepository extends JpaRepository<KnowledgeChunk, String> {
    List<KnowledgeChunk> findByDocumentIdOrderByPositionAsc(String documentId);
    void deleteByDocumentId(String documentId);
}
