package cn.edu.campus.kb;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.springframework.stereotype.Component;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class DocumentParser {
    public String parse(String filename, byte[] bytes) {
        String extension = extension(filename);
        if (bytes.length == 0) throw ApiException.badRequest("文件内容为空");
        if (bytes.length > 10 * 1024 * 1024) throw ApiException.badRequest("文件不能超过 10 MB");
        String text;
        try {
            text = switch (extension) {
                case "txt", "md" -> decode(bytes);
                case "pdf" -> {
                    try (var pdf = Loader.loadPDF(bytes)) {
                        if (pdf.getNumberOfPages() > 300) throw ApiException.badRequest("PDF 最多支持 300 页");
                        yield new PDFTextStripper().getText(pdf);
                    }
                }
                case "docx" -> {
                    try (var doc = new XWPFDocument(new ByteArrayInputStream(bytes));
                         var extractor = new XWPFWordExtractor(doc)) {
                        yield extractor.getText();
                    }
                }
                default -> throw ApiException.badRequest("仅支持 PDF、DOCX、TXT、MD 文件");
            };
        } catch (ApiException ex) { throw ex; }
        catch (Exception ex) { throw ApiException.badRequest("无法解析文件，请检查文件格式、编码或是否加密"); }
        text = text.replace("\uFEFF", "").replace("\u0000", "").replace("\r\n", "\n")
                .replace('\r', '\n').replaceAll("[\\t ]+", " ").replaceAll("\n{3,}", "\n\n").strip();
        if (text.length() < 10) throw ApiException.badRequest("可提取文字不足；扫描版 PDF 请先进行 OCR 识别");
        if (text.length() > 200_000) throw ApiException.badRequest("单份文档最多支持 20 万字，请拆分后上传");
        return text;
    }

    private String decode(byte[] bytes) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
    }

    public String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public List<String> split(String text) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + 650, text.length());
            if (end < text.length()) {
                int boundary = Math.max(text.lastIndexOf('\n', end), text.lastIndexOf('。', end));
                if (boundary > start + 325) end = boundary + 1;
            }
            String chunk = text.substring(start, end).strip();
            if (!chunk.isBlank()) chunks.add(chunk);
            if (end == text.length()) break;
            start = end - 80;
        }
        return chunks;
    }
}
