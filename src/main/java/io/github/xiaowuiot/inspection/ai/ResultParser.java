package io.github.xiaowuiot.inspection.ai;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class ResultParser {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private ResultParser() {
    }

    /**
     * 模型偶尔会在 JSON 前后加说明文字或 ```json 代码块，这里只取第一个 { 到最后一个 } 之间的内容。
     */
    public static AnalysisResult parse(String text) {
        return read(text, AnalysisResult.class);
    }


    private static <T> T read(String text, Class<T> type) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new AnalyzeException("模型没有返回 JSON：" + abbreviate(text));
        }
        try {
            return MAPPER.readValue(text.substring(start, end + 1), type);
        } catch (Exception e) {
            throw new AnalyzeException("模型返回的 JSON 解析失败：" + abbreviate(text), e);
        }
    }

    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String abbreviate(String s) {
        return s.length() > 300 ? s.substring(0, 300) + "…" : s;
    }
}
