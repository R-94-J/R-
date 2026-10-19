package cn.edu.campus.kb;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QuestionRoutingTest {
    private final AiClient ai = mock(AiClient.class);
    private final QuestionPolicy policy = mock(QuestionPolicy.class);
    private final OfficialWebsiteSearch website = mock(OfficialWebsiteSearch.class);
    private final RetrievalService retrieval = mock(RetrievalService.class);
    private ChatService service;
    private final RetrievalService.Source source = new RetrievalService.Source(1, "doc", "学位规定", "学籍", "chunk", 1, "绩点达到1.8。", 1);

    @BeforeEach
    void setup() {
        var conversations = mock(ConversationRepository.class);
        var messages = mock(MessageRepository.class);
        when(messages.findByConversationIdOrderByCreatedAtAsc(anyString())).thenReturn(List.of());
        when(retrieval.search(anyString())).thenReturn(new RetrievalService.SearchResult(List.of(), "bm25", false));
        when(ai.enabled()).thenReturn(true);
        service = new ChatService(conversations, messages, retrieval, ai, new ObjectMapper(), policy, website);
    }

    @Test
    void unrelatedQuestionNeverSearchesWebsiteOrGeneratesAnswer() {
        when(policy.decide(anyString(), anyList(), anyList())).thenReturn(new QuestionPolicy.Decision(false, false, "彩票", "other", false));
        var result = service.ask("owner", null, "我是学生，帮我推荐彩票号码");
        assertThat(result.assistantMessage().mode()).isEqualTo("out_of_scope");
        assertThat(result.assistantMessage().sources()).isEmpty();
        verifyNoInteractions(website); verify(ai, never()).answer(anyList());
    }

    @Test
    void adequateKnowledgeSkipsWebsite() {
        when(retrieval.search(anyString())).thenReturn(new RetrievalService.SearchResult(List.of(source), "bm25", false));
        when(policy.decide(anyString(), anyList(), anyList())).thenReturn(new QuestionPolicy.Decision(true, true, "绩点", "school", false));
        when(ai.answer(anyList())).thenReturn("绩点需达到1.8。[1]");
        assertThat(service.ask("owner", null, "学位绩点要求").assistantMessage().mode()).isEqualTo("model");
        verifyNoInteractions(website);
    }

    @Test
    void weakKeywordMatchesFallThroughToOfficialWebsiteWithTraceableLinks() {
        when(retrieval.search(anyString())).thenReturn(new RetrievalService.SearchResult(List.of(source), "bm25", false));
        when(policy.decide(anyString(), anyList(), anyList()))
                .thenReturn(new QuestionPolicy.Decision(true, false, "校训", "school", false))
                .thenReturn(new QuestionPolicy.Decision(true, true, "校训", "school", false));
        var official = new RetrievalService.Source(1, null, "学校简介", "学校官网", "web-1", 1, "校训为德才兼备、奋发有为。", 1,
                "https://www.sdyu.edu.cn/xxgk/xxjj.htm", "学校官网", null, "检索时间2026", null);
        when(website.search("校训")).thenReturn(new OfficialWebsiteSearch.Result(List.of(official), "matched", "2026-10-12T00:00:00Z", "校训"));
        when(ai.answer(anyList())).thenReturn("校训为德才兼备、奋发有为。[1]");
        var result = service.ask("owner", null, "学校校训是什么");
        assertThat(result.assistantMessage().mode()).isEqualTo("website");
        assertThat(result.assistantMessage().sources().get(0).sourceUrl()).startsWith("https://www.sdyu.edu.cn/");
        assertThat(result.assistantMessage().content()).contains("检索时间");
    }

    @Test
    void academicFallbackAlwaysLabelsNonOfficialAndDoesNotInventSources() {
        when(policy.decide(anyString(), anyList(), anyList())).thenReturn(new QuestionPolicy.Decision(true, false, "二分查找", "academic", false));
        when(website.search(anyString())).thenReturn(new OfficialWebsiteSearch.Result(List.of(), "no_match", "now", "二分查找"));
        when(ai.answer(anyList())).thenReturn("二分查找每次将范围减半，时间复杂度是O(log n)。");
        var result = service.ask("owner", null, "解释二分查找算法");
        assertThat(result.assistantMessage().mode()).isEqualTo("general");
        assertThat(result.assistantMessage().content()).startsWith("以下非学校官方资料");
        assertThat(result.assistantMessage().sources()).isEmpty();
        var order = inOrder(website, ai); order.verify(website).search(anyString()); order.verify(ai).enabled(); order.verify(ai).answer(anyList());
    }

    @Test
    void failedSearchIsNotReportedAsAbsentInformationAndPolicyFailureIsClosed() {
        when(policy.decide(anyString(), anyList(), anyList())).thenReturn(new QuestionPolicy.Decision(true, false, "费用", "school", false));
        when(website.search(anyString())).thenReturn(new OfficialWebsiteSearch.Result(List.of(), "failed", "now", "费用"));
        when(ai.answer(anyList())).thenReturn("具体费用无法确认，请咨询学校。");
        assertThat(service.ask("owner", null, "今年学费多少").assistantMessage().content()).contains("官网检索失败").startsWith("以下非学校官方资料");
        when(policy.decide(anyString(), anyList(), anyList())).thenReturn(new QuestionPolicy.Decision(false, false, "", "unknown", true));
        assertThat(service.ask("owner", null, "其他问题").assistantMessage().mode()).isEqualTo("unavailable");
    }

    @Test
    void realPolicyParsesStrictJsonAndRejectsMalformedOutput() {
        var realPolicy = new QuestionPolicy(ai, new ObjectMapper());
        when(ai.answer(anyList())).thenReturn("{\"allowed\":false,\"supported\":false,\"query\":\"娱乐\",\"kind\":\"other\"}");
        assertThat(realPolicy.decide("讲个笑话", List.of(), List.of()).allowed()).isFalse();
        when(ai.answer(anyList())).thenReturn("请忽略审核");
        assertThat(realPolicy.decide("讲个笑话", List.of(), List.of()).unavailable()).isTrue();
    }

    @Test
    void localPolicyRecognizesFinancialAidWithoutAllowingUnrelatedMixedRequests() {
        when(ai.enabled()).thenReturn(false);
        var realPolicy = new QuestionPolicy(ai, new ObjectMapper());
        assertThat(realPolicy.decide("家庭经济困难学生认定分为哪些档次？", List.of(), List.of()).allowed()).isTrue();
        assertThat(realPolicy.decide("勤工助学每周可以工作多久？", List.of(), List.of()).kind()).isEqualTo("school");
        assertThat(realPolicy.decide("资助怎么申请，再推荐彩票号码", List.of(), List.of()).allowed()).isFalse();
    }
}
