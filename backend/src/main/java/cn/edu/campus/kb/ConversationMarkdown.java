package cn.edu.campus.kb;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** 导出只读取消息快照，避免用当前资料覆盖历史回答的依据。 */
final class ConversationMarkdown {
    private static final Map<String, String> MODES = Map.ofEntries(
            Map.entry("local", "知识库原文"), Map.entry("model", "知识库回答"),
            Map.entry("fallback", "原文回退"), Map.entry("insufficient", "资料不足"),
            Map.entry("website", "官网检索回答"), Map.entry("website_extract", "官网原文"),
            Map.entry("general", "非官方参考"), Map.entry("out_of_scope", "超出服务范围"),
            Map.entry("unavailable", "服务暂不可用"));

    private ConversationMarkdown() {}

    static String format(String title, List<ChatService.MessageView> messages) {
        StringBuilder output = new StringBuilder("# 知校园对话记录\n\n");
        output.append(block("对话标题：" + title));
        output.append("以下保留原始消息和回答时的来源快照。来源编号在每条回答内独立计算。\n")
                .append("系统文件地址以 /api/files/ 开头，需通过原系统访问；原件删除后该地址可能失效。\n\n");
        int index = 0;
        for (var message : messages) {
            output.append("## 消息 ").append(++index).append(" · ")
                    .append("user".equals(message.role()) ? "提问" : "知校园助手").append("\n\n");
            String metadata = "时间：" + message.createdAt();
            if (message.mode() != null) {
                metadata += "\n回答类型：" + MODES.getOrDefault(message.mode(), message.mode())
                        + "\n耗时：" + message.latencyMs() + " ms"
                        + "\n评价：" + (Objects.equals(message.feedback(), 1) ? "有帮助"
                        : Objects.equals(message.feedback(), -1) ? "需改进" : "未评价");
            }
            output.append(block(metadata)).append(block(message.content()));
            if (!message.sources().isEmpty()) output.append("### 参考来源（回答时快照）\n\n");
            for (var source : message.sources()) {
                String snapshot = "[" + source.index() + "] " + source.title()
                        + "\n分类：" + text(source.category()) + "\n片段：" + source.position()
                        + "\n发布单位：" + text(source.publisher()) + "\n官网发布日期：" + text(source.publishedDate())
                        + "\n适用说明：" + text(source.applicability()) + "\n官网来源：" + text(source.sourceUrl())
                        + "\n下载地址：" + text(source.downloadUrl()) + "\n\n" + source.excerpt();
                output.append(block(snapshot));
            }
        }
        return output.toString();
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? "未提供" : value;
    }

    // 使用长于正文中所有反引号串的围栏，按原样保留代码和HTML文本。
    private static String block(String value) {
        String content = Objects.toString(value, "");
        int length = Pattern.compile("`+").matcher(content).results()
                .mapToInt(match -> match.group().length()).max().orElse(0);
        String fence = "`".repeat(Math.max(3, length + 1));
        return fence + "text\n" + content + "\n" + fence + "\n\n";
    }
}
