package io.github.xiaowuiot.inspection;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class InspectionApplication {

    public static void main(String[] args) {
        // 统计"每天"的识别次数按北京时间切日，不受服务器时区影响
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        SpringApplication.run(InspectionApplication.class, args);
    }
}
