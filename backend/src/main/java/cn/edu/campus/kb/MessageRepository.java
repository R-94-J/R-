package cn.edu.campus.kb;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

interface MessageRepository extends JpaRepository<ChatMessage, String> {
    long countByRole(String role);
    long countByFeedback(Integer feedback);
    List<ChatMessage> findByConversationIdOrderByCreatedAtAsc(String conversationId);
    void deleteByConversationId(String conversationId);
}
