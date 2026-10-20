package io.github.xiaowuiot.inspection.inspection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 早期版本把模型调用的原始报错（带接口地址、模型返回的原文）记到了识别记录里，页面上用户能看到。
 * 现在只记中文提示（见 ModelCalls），启动时把老记录也换成对应的中文提示；已经换过的不会再匹配，重复执行没有影响。
 */
@Component
public class LegacyErrorCleanup implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LegacyErrorCleanup.class);

    private static final String[][] RULES = {
            {"I/O error%", "识别服务暂时连不上，请稍后重试"},
            {"调用模型失败 HTTP 429%", "识别服务繁忙，请稍后重试"},
            {"调用模型失败 HTTP 5%", "识别服务暂时不可用，请稍后重试"},
            {"调用模型失败 HTTP 401%", "识别服务配置有误，请联系管理员"},
            {"调用模型失败 HTTP 403%", "识别服务配置有误，请联系管理员"},
            {"调用模型失败 HTTP%", "识别请求出错，请换一张照片再试"},
            {"模型响应里没有文本%", "识别结果为空，请重试"},
    };

    private final JdbcTemplate jdbc;

    public LegacyErrorCleanup(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        int total = 0;
        for (String[] r : RULES) {
            try {
                total += jdbc.update("UPDATE inspections SET error = ? WHERE error LIKE ?", r[1], r[0]);
            } catch (RuntimeException e) {
                log.warn("整理识别记录的报错文字跳过：{}", e.getMessage());
                return;
            }
        }
        if (total > 0) {
            log.info("已把 {} 条识别记录的原始报错换成中文提示", total);
        }
    }
}
