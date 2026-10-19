package io.github.xiaowuiot.inspection.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param dataDir       数据目录：数据库和照片都在这里
 * @param allowRegister 是否开放注册；关掉后只能用管理员账号
 * @param promo         手机端"我的"页面是否显示作者公众号和知识星球入口
 */
@ConfigurationProperties(prefix = "inspection")
public record InspectionProperties(@DefaultValue("./data") String dataDir, Ai ai,
                                   @DefaultValue("true") boolean allowRegister,
                                   @DefaultValue("true") boolean promo) {

    public record Ai(String provider, String apiKey, String model, String baseUrl,
                     @DefaultValue("90") int timeoutSeconds) {

        public boolean hasKey() {
            return apiKey != null && !apiKey.isBlank();
        }
    }
}
