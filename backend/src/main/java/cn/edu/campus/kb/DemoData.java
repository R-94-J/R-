package cn.edu.campus.kb;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class DemoData implements CommandLineRunner {
    private final DocumentRepository documents;
    private final DocumentService service;
    private final boolean enabled;

    public DemoData(DocumentRepository documents, DocumentService service, @Value("${app.seed-demo}") boolean enabled) {
        this.documents = documents; this.service = service; this.enabled = enabled;
    }

    @Override
    public void run(String... args) throws Exception {
        if (!enabled || documents.count() != 0) return;
        String[][] samples = {{"library.md", "图书馆借阅与开放指南（示例）", "学习资源"},
                {"campus-card.md", "校园卡办理指南（示例）", "校园生活"},
                {"student-affairs.md", "学生请假与证明办理指南（示例）", "教务办事"}};
        for (String[] sample : samples) {
            try (var input = new ClassPathResource("demo/" + sample[0]).getInputStream()) {
                service.upload(sample[0], input.readAllBytes(), sample[1], sample[2], true);
            }
        }
    }
}
