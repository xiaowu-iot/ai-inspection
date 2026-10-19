package io.github.xiaowuiot.inspection.ai;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import io.github.xiaowuiot.inspection.config.InspectionProperties;

/**
 * 调用 OpenAI 兼容的 /chat/completions 接口。国内的通义千问、豆包等多模态模型都提供这种接口，
 * 换模型只需要改 base-url 和 model。
 */
public class OpenAiCompatibleAnalyzer extends LlmAnalyzer {

    private final RestClient http;
    private final String model;

    public OpenAiCompatibleAnalyzer(RestClient.Builder builder, InspectionProperties.Ai ai) {
        if (ai.baseUrl() == null || ai.baseUrl().isBlank()) {
            throw new IllegalStateException("provider=openai 时必须配置 inspection.ai.base-url");
        }
        this.http = builder.baseUrl(ai.baseUrl())
                .defaultHeader("Authorization", "Bearer " + ai.apiKey())
                .build();
        this.model = ai.model();
    }

    @Override
    protected Completion complete(byte[] jpeg, List<Turn> turns) {
        List<Map<String, Object>> messages = new ArrayList<>();
        for (int i = 0; i < turns.size(); i++) {
            Turn t = turns.get(i);
            byte[] image = i == 0 ? jpeg : t.image();
            Object content = image != null
                    ? List.of(Map.of("type", "image_url", "image_url",
                                    Map.of("url", "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(image))),
                            Map.of("type", "text", "text", t.text()))
                    : t.text();
            messages.add(Map.of("role", t.role(), "content", content));
        }
        Map<String, Object> body = Map.of("model", model, "max_tokens", Prompts.MAX_OUTPUT_TOKENS, "messages", messages);
        JsonNode resp;
        try {
            resp = http.post().uri("/chat/completions").body(body).retrieve().body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw new AnalyzeException("调用模型失败 HTTP " + e.getStatusCode().value() + "：" + e.getResponseBodyAsString(), e);
        }
        JsonNode choice = resp.path("choices").path(0);
        if ("length".equals(choice.path("finish_reason").asText())) {
            throw new AnalyzeException("模型输出超长被截断，请重拍一张范围小一些的照片");
        }
        String text = choice.path("message").path("content").asText("");
        if (text.isEmpty()) {
            throw new AnalyzeException("模型响应里没有文本：" + resp);
        }
        JsonNode usage = resp.path("usage");
        return new Completion(text, usage.path("prompt_tokens").asInt(), usage.path("completion_tokens").asInt());
    }

    @Override
    public String name() {
        return model;
    }
}
