package cn.edu.campus.kb;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

interface ConversationRepository extends JpaRepository<Conversation, String> {
    List<Conversation> findByOwnerKeyOrderByUpdatedAtDesc(String ownerKey);
    Optional<Conversation> findByIdAndOwnerKey(String id, String ownerKey);
}
