package cn.edu.campus.kb;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;

class DocumentParserTest {
    private final DocumentParser parser = new DocumentParser();

    @Test
    void parsesUtf8AndRejectsUnsupportedEmptyAndMalformedFiles() {
        assertThat(parser.parse("guide.MD", "\uFEFF校园指南\r\n图书馆每日早上八点开门。".getBytes(StandardCharsets.UTF_8)))
                .startsWith("校园指南\n").doesNotContain("\uFEFF");
        assertThatThrownBy(() -> parser.parse("test.exe", new byte[]{1, 2})).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> parser.parse("test.txt", new byte[0])).hasMessageContaining("为空");
        assertThatThrownBy(() -> parser.parse("test.txt", new byte[]{(byte) 0xc3, (byte) 0x28})).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> parser.parse("scan.pdf", "bad pdf".getBytes())).isInstanceOf(ApiException.class);
    }

    @Test
    void parsesRealDocxAndPdf() throws Exception {
        try (var word = new XWPFDocument(); var output = new ByteArrayOutputStream()) {
            word.createParagraph().createRun().setText("校园卡补办需要携带身份证和学生证。");
            word.write(output);
            assertThat(parser.parse("guide.docx", output.toByteArray())).contains("身份证和学生证");
        }
        try (var pdf = new PDDocument(); var output = new ByteArrayOutputStream()) {
            var page = new PDPage(); pdf.addPage(page);
            try (var stream = new PDPageContentStream(pdf, page)) {
                stream.beginText(); stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(30, 700); stream.showText("Library opens at 08:00 every day."); stream.endText();
            }
            pdf.save(output);
            assertThat(parser.parse("guide.pdf", output.toByteArray())).contains("Library opens at 08:00");
        }
    }

    @Test
    void chunkingPreservesEndOfLongDocumentAndOverlaps() {
        String text = "校园图书馆借阅指南。\n".repeat(150) + "尾部不可丢失";
        var chunks = parser.split(text);
        assertThat(chunks).hasSizeGreaterThan(2).allMatch(chunk -> !chunk.isBlank() && chunk.length() <= 651);
        assertThat(chunks.get(chunks.size() - 1)).endsWith("尾部不可丢失");
        assertThat(chunks.get(1)).startsWith(chunks.get(0).substring(chunks.get(0).length() - 79).strip());
    }

    @Test
    void citationValidationRejectsMissingOutOfRangeAndOverflow() {
        assertThat(ChatService.validCitations("材料为学生证。[1]", 2)).isTrue();
        assertThat(ChatService.validCitations("无引用的回答", 2)).isFalse();
        assertThat(ChatService.validCitations("回答[1][3]", 2)).isFalse();
        assertThat(ChatService.validCitations("回答[99999999999999999999]", 2)).isFalse();
    }
}
