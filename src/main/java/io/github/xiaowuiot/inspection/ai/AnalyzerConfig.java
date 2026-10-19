package io.github.xiaowuiot.inspection.ai;

import java.net.http.HttpClient;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import io.github.xiaowuiot.inspection.config.InspectionProperties;

@Configuration
public class AnalyzerConfig {

    private static final Logger log = LoggerFactory.getLogger(AnalyzerConfig.class);

    @Bean
    public HazardAnalyzer hazardAnalyzer(InspectionProperties props, RestClient.Builder builder) {
        InspectionProperties.Ai ai = props.ai();
        String provider = ai.provider() == null ? "mock" : ai.provider().trim().toLowerCase();
        if (!"mock".equals(provider) && !ai.hasKey()) {
            log.warn("provider={} 但没有配置 API key，改用演示数据", provider);
            provider = "mock";
        }

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build());
        factory.setReadTimeout(Duration.ofSeconds(ai.timeoutSeconds()));
        builder.requestFactory(factory);

        HazardAnalyzer analyzer = switch (provider) {
            case "openai" -> new OpenAiCompatibleAnalyzer(builder, ai);
            default -> new MockAnalyzer();
        };
        log.info("隐患分析使用：{}", analyzer.name());
        return analyzer;
    }
}
