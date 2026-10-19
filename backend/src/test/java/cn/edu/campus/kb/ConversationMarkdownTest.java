package cn.edu.campus.kb;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ConversationMarkdownTest {
    @Test
    void exportPreservesLiteralCodeAndNonOfficialLabels() {
        var message = new ChatService.MessageView("message", "assistant",
                "```java\nSystem.out.println(1);\n```\n<script>alert(1)</script>",
                "general", List.of(), 150, -1, LocalDateTime.of(2026, 10, 15, 10, 0));
        String markdown = ConversationMarkdown.format("<b>代码笔记</b>", List.of(message));
        assertThat(markdown).contains("非官方参考", "需改进", "150 ms",
                "````text\n```java\nSystem.out.println(1);\n```\n<script>alert(1)</script>\n````")
                .doesNotContain("### 参考来源");
    }
}
