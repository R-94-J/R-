package cn.edu.campus.kb;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

interface DocumentRepository extends JpaRepository<KnowledgeDocument, String> {
    List<KnowledgeDocument> findAllByOrderByCreatedAtDesc();
    boolean existsByContentHash(String contentHash);
}
