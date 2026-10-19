package cn.edu.campus.kb;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class OfficialWebsiteSearchTest {
    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "liveWebsite", matches = "true")
    void liveSchoolOverviewCanBeRead() {
        var result = new OfficialWebsiteSearch(true, 5000, 4, new DocumentParser()).search("校训");
        assertThat(result.sources()).anySatisfy(source -> {
            assertThat(source.sourceUrl()).isEqualTo("https://www.sdyu.edu.cn/xxgk/xxjj.htm");
            assertThat(source.excerpt()).contains("自强不息");
        });
    }

    @Test
    void searchReadsOfficialResultsAndIgnoresExternalRedirectTargets() {
        List<String> calls = new ArrayList<>();
        var service = new OfficialWebsiteSearch(true, 1000, 2, new DocumentParser()) {
            @Override protected Document fetch(String url, Map<String, String> form) {
                calls.add(url);
                if (url.contains("search_list")) return Jsoup.parse("<a href='/info/1004/123.htm'>课程咨询电话</a><a href='https://evil.test/info/1'>伪造页面</a>", url);
                if (url.contains("/info/")) return Jsoup.parse("<title>课程咨询电话</title><p>发布时间：2026-10-12</p><div class='v_news_content'>课程咨询电话由教务部门公布，请按照学校通知联系对应科室。电话为0531-12345678。请在工作日办公时间咨询课程和选课事项，核实课程安排与要求。</div><a href='/files/form.docx'>申请表.docx</a>", url);
                return Jsoup.parse("<html></html>", url);
            }
        };
        var result = service.search("课程咨询");
        assertThat(result.sources()).isNotEmpty();
        assertThat(result.sources().get(0).downloadUrl()).isEqualTo("https://www.sdyu.edu.cn/files/form.docx");
        assertThat(result.sources().get(0).publishedDate()).isEqualTo("2026-10-12");
        assertThat(result.sources().get(0).applicability()).contains("官网在线检索时间");
        assertThat(calls).noneMatch(url -> url.contains("evil"));
    }

    @Test
    void exactHostAllowlistAndContentExtractionRejectUnsafeOrEmptyPages() {
        for (String url : List.of("http://www.sdyu.edu.cn/", "https://www.sdyu.edu.cn.evil.test/", "https://127.0.0.1/", "https://user@www.sdyu.edu.cn/", "https://www.sdyu.edu.cn:8080/"))
            assertThat(OfficialWebsiteSearch.allowedUrl(url)).isFalse();
        var service = new OfficialWebsiteSearch(true, 1000, 2, new DocumentParser());
        assertThat(service.extract(Jsoup.parse("<div>导航，无正文</div>"), "https://www.sdyu.edu.cn/", "导航", "now")).isEmpty();
        assertThat(new OfficialWebsiteSearch(false, 1000, 2, new DocumentParser()).search("课程").status()).isEqualTo("disabled");
    }
}
