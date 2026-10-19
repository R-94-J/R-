package cn.edu.campus.kb;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class OfficialWebsiteSearch {
    public record Result(List<RetrievalService.Source> sources, String status, String searchedAt, String query) {}
    private final boolean enabled;
    private final int timeout;
    private final int maxPages;
    private final DocumentParser parser;
    private static final Set<String> HOSTS = Set.of("www.sdyu.edu.cn", "jwc.sdyu.edu.cn", "lib.sdyu.edu.cn", "xsc.sdyu.edu.cn");
    private static final Pattern DATE = Pattern.compile("(?:发布时间|日期|发布于|时间)\\s*[：:]?\\s*(20\\d{2}[-/.]\\d{1,2}[-/.]\\d{1,2})");

    public OfficialWebsiteSearch(@Value("${app.website.enabled:true}") boolean enabled,
                                 @Value("${app.website.timeout-ms:5000}") int timeout,
                                 @Value("${app.website.max-pages:4}") int maxPages, DocumentParser parser) {
        this.enabled = enabled; this.timeout = Math.max(500, Math.min(timeout, 10000));
        this.maxPages = Math.max(1, Math.min(maxPages, 6)); this.parser = parser;
    }

    static boolean allowedUrl(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && HOSTS.contains(uri.getHost())
                    && (uri.getPort() == -1 || uri.getPort() == 443) && uri.getUserInfo() == null;
        } catch (Exception ex) { return false; }
    }

    protected Document fetch(String url, Map<String, String> form) throws Exception {
        // Only reviewed public official hosts; never send model credentials or browser cookies.
        if (!allowedUrl(url)) throw new IllegalArgumentException("Non-official URL");
        var connection = Jsoup.connect(url).userAgent("CampusKnowledgeBase/1.0 (school public information search)")
                .timeout(timeout).maxBodySize(1_500_000).followRedirects(false);
        if (!form.isEmpty()) connection.data(form).method(org.jsoup.Connection.Method.POST);
        var response = connection.execute();
        if (response.statusCode() != 200 || response.contentType() == null || !response.contentType().contains("text/html"))
            throw new IllegalStateException("Official page unavailable");
        return response.parse();
    }

    public Result search(String query) {
        String now = Instant.now().toString();
        if (!enabled) return new Result(List.of(), "disabled", now, query);
        long deadline = System.nanoTime() + 25_000_000_000L;
        Map<String, String> form = Map.of("lucenenewssearchkey", Base64.getEncoder().encodeToString(query.getBytes(StandardCharsets.UTF_8)),
                "_lucenesearchtype", "1", "searchScope", "0", "showkeycode", query);
        LinkedHashMap<String, String> links = new LinkedHashMap<>();
        List<RetrievalService.Source> found = new ArrayList<>();
        int successes = 0, failures = 0;
        try {
            Document results = fetch("https://www.sdyu.edu.cn/search_list.jsp?wbtreeid=1001", form);
            collect(results, links); successes++;
        } catch (Exception ex) { failures++; }
        // Fixed official overview pages cover questions that the news-only search does not index well.
        if (query.matches(".*(校训|简介|建校|地址|校区|历史).*")) links.put("https://www.sdyu.edu.cn/xxgk/xxjj.htm", "学校简介");
        if (query.matches(".*(领导|校长|院长|书记).*")) links.put("https://www.sdyu.edu.cn/xxgk/xrld1.htm", "现任领导");
        if (query.matches(".*(电话|联系).*")) links.put("https://www.sdyu.edu.cn/bgdh1.htm", "办公电话");
        String host = query.matches(".*(图书|借阅|座位|数据库资源).*") ? "lib.sdyu.edu.cn" :
                query.matches(".*(助学|奖学|学生管理|资助|宿舍|申诉).*") ? "xsc.sdyu.edu.cn" : "jwc.sdyu.edu.cn";
        try {
            if (System.nanoTime() < deadline) {
                Document home = fetch("https://" + host + "/", Map.of());
                Element searchForm = home.select("form").stream().filter(f -> !f.select("input[name=lucenenewssearchkey]").isEmpty()).findFirst().orElse(null);
                if (searchForm != null) {
                    String action = searchForm.absUrl("action").replace("http://", "https://");
                    if (allowedUrl(action)) { collect(fetch(action, form), links); successes++; }
                }
            }
        } catch (Exception ex) { failures++; }
        var ranked = links.entrySet().stream().sorted(Comparator.comparingDouble((Map.Entry<String, String> e) ->
                relevance(query, e.getValue()) + (e.getKey().contains("/xxgk/") || e.getKey().endsWith("/bgdh1.htm") ? 2 : 0)).reversed()).limit(maxPages).toList();
        for (var link : ranked) {
            if (System.nanoTime() >= deadline) { failures++; break; }
            try {
                var page = fetch(link.getKey(), Map.of()); successes++;
                found.addAll(extract(page, link.getKey(), query, now));
            } catch (Exception ex) { failures++; }
        }
        found.sort(Comparator.comparingDouble(RetrievalService.Source::score).reversed());
        List<RetrievalService.Source> output = new ArrayList<>();
        for (var source : found.stream().limit(5).toList()) output.add(new RetrievalService.Source(output.size() + 1, null, source.title(), "学校官网",
                source.chunkId(), source.position(), source.excerpt(), source.score(), source.sourceUrl(), source.publisher(),
                source.publishedDate(), source.applicability(), source.downloadUrl()));
        org.slf4j.LoggerFactory.getLogger(OfficialWebsiteSearch.class).info("Official search query={}, pages={}, snippets={}, failures={}", query, ranked.size(), output.size(), failures);
        return new Result(output, successes == 0 ? "failed" : failures > 0 ? "partial" : output.isEmpty() ? "no_match" : "matched", now, query);
    }

    static void collect(Document page, Map<String, String> links) {
        for (Element anchor : page.select("a[href]")) {
            String url = anchor.absUrl("href").replace("http://", "https://");
            if (allowedUrl(url) && (url.contains("/info/") || url.contains("NewsContentUrl")) && !anchor.text().isBlank())
                links.putIfAbsent(url, anchor.text());
        }
    }

    List<RetrievalService.Source> extract(Document page, String url, String query, String searchedAt) {
        Element article = page.selectFirst(".v_news_content");
        if (article == null) article = page.selectFirst("#vsb_content, #vsb_content_2, #vsb_content_4, .arc-con [id^=vsb_content]");
        if (article == null) return List.of();
        article.select("script,style,iframe,form").remove();
        String text = article.wholeText().replaceAll("[\\t ]+", " ").replaceAll("\\n{3,}", "\n\n").strip();
        if (text.length() < 60 || text.contains("请输入验证码")) return List.of();
        if (text.length() > 18000) text = text.substring(0, 18000);
        String title = page.title().split("-欢迎|—欢迎|-山东青年")[0];
        String published = null;
        var date = DATE.matcher(page.text()); if (date.find()) published = date.group(1);
        String download = null;
        for (Element a : page.select("a[href]")) {
            String target = a.absUrl("href").replace("http://", "https://");
            if (allowedUrl(target) && a.text().matches("(?i).*\\.(docx?|pdf)$")) { download = target; break; }
        }
        List<RetrievalService.Source> snippets = new ArrayList<>();
        int position = 0;
        for (String chunk : parser.split(text)) {
            position++;
            double score = relevance(query, chunk);
            if (score < 0.2) continue;
            snippets.add(new RetrievalService.Source(0, null, title, "学校官网", "web-" + UUID.randomUUID(), position, chunk, score,
                    url, "山东青年政治学院官网", published, "官网在线检索时间：" + searchedAt + "；按页面日期和适用范围理解，最终以学校最新通知为准。", download));
        }
        return snippets.stream().sorted(Comparator.comparingDouble(RetrievalService.Source::score).reversed()).limit(2).toList();
    }

    static double relevance(String query, String text) {
        Set<String> terms = new HashSet<>(RetrievalService.tokenize(query));
        if (terms.isEmpty()) return 0;
        String value = text.toLowerCase(Locale.ROOT);
        return terms.stream().filter(value::contains).count() / (double) terms.size();
    }
}
