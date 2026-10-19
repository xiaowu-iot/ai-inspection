package io.github.xiaowuiot.inspection.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class ResultParserTest {

    @Test
    void parsesJsonWrappedInCodeFence() {
        String text = """
                好的，分析如下：
                ```json
                {"scene":"配电房","hazards":[{"category":"电气","description":"箱门敞开","level":"较大","advice":"关门上锁","box":[100,200,300,400]}],
                 "cannotJudge":["是否带电"],"confidence":"高","extra":"未知字段应忽略"}
                ```
                """;
        AnalysisResult r = ResultParser.parse(text);
        assertEquals("配电房", r.scene());
        assertEquals(1, r.hazards().size());
        assertEquals(List.of(100, 200, 300, 400), r.hazards().get(0).box());
        assertEquals(List.of("是否带电"), r.cannotJudge());
    }

    @Test
    void missingListsBecomeEmpty() {
        AnalysisResult r = ResultParser.parse("{\"scene\":\"空地\",\"confidence\":\"中\"}");
        assertTrue(r.hazards().isEmpty());
        assertTrue(r.cannotJudge().isEmpty());
    }

    @Test
    void rejectsNonJson() {
        assertThrows(AnalyzeException.class, () -> ResultParser.parse("抱歉，我无法查看这张图片。"));
    }
}
