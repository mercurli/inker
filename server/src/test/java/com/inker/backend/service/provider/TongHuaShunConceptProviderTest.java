package com.inker.backend.service.provider;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TongHuaShunConceptProviderTest {

    private final TongHuaShunConceptProvider provider = new TongHuaShunConceptProvider();

    @Test
    void extractConceptsFromHtml_shouldParseJPopLinkMarkup() {
        String html = """
                <li>
                    <a topStock="603115,603788,600261" class="J_popLink " href="javascript:void(0);" cid="309124"
                       tag="高股息精选">高股息精选</a>
                </li>
                <li>
                    <a topStock="600743,002338,600736" class="J_popLink " href="javascript:void(0);" cid="301715"
                       tag="证金持股">证金持股</a>
                </li>
                """;

        List<String> concepts = provider.extractConceptsFromHtml(html);
        assertEquals(List.of("高股息精选", "证金持股"), concepts);
    }

    @Test
    void extractConceptsFromHtml_shouldParseLegacyMarkup() {
        String html = """
                <a class="gnStockList" href="javascript:;" tag="17-中字头股票"></a>
                <a class="gnStockList" href="javascript:;" tag="26-国企改革"></a>
                """;

        List<String> concepts = provider.extractConceptsFromHtml(html);
        assertEquals(List.of("中字头股票", "国企改革"), concepts);
    }
}
