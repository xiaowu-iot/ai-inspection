package io.github.xiaowuiot.inspection.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

class ModelCallsTest {

    @BeforeAll
    static void noDelay() {
        ModelCalls.retryDelayMillis = 0;
    }

    private static ResourceAccessException reset() {
        return new ResourceAccessException("I/O error on POST request for \"https://api.example.com/v1/messages\": Connection reset",
                new IOException("Connection reset"));
    }

    @Test
    void connectionResetIsRetriedOnce() {
        AtomicInteger n = new AtomicInteger();
        String r = ModelCalls.call(() -> {
            if (n.incrementAndGet() == 1) {
                throw reset();
            }
            return "ok";
        });
        assertEquals("ok", r);
        assertEquals(2, n.get());
    }

    @Test
    void finalFailureShowsChineseHintWithoutUrl() {
        AtomicInteger n = new AtomicInteger();
        AnalyzeException e = assertThrows(AnalyzeException.class, () -> ModelCalls.call(() -> {
            n.incrementAndGet();
            throw reset();
        }));
        assertEquals(2, n.get());
        assertEquals("识别服务暂时连不上，请稍后重试", e.getMessage());
        assertFalse(e.getMessage().contains("http"));
    }

    @Test
    void httpErrorsAreTranslatedAndOnlyTransientOnesRetried() {
        AtomicInteger n = new AtomicInteger();
        AnalyzeException busy = assertThrows(AnalyzeException.class, () -> ModelCalls.call(() -> {
            n.incrementAndGet();
            throw new AnalyzeException("调用模型失败 HTTP 429：{...}", HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "", null, null, null));
        }));
        assertEquals("识别服务繁忙，请稍后重试", busy.getMessage());
        assertEquals(2, n.get());

        n.set(0);
        AnalyzeException auth = assertThrows(AnalyzeException.class, () -> ModelCalls.call(() -> {
            n.incrementAndGet();
            throw new AnalyzeException("调用模型失败 HTTP 401：{\"error\":\"invalid x-api-key\"}", HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "", null, null, null));
        }));
        assertEquals("识别服务配置有误，请联系管理员", auth.getMessage());
        assertEquals(1, n.get());

        assertTrue(ModelCalls.transient_(HttpServerErrorException.create(HttpStatus.BAD_GATEWAY, "", null, null, null)));
        assertEquals("识别服务暂时不可用，请稍后重试", ModelCalls.friendly(HttpServerErrorException.create(HttpStatus.BAD_GATEWAY, "", null, null, null)));
    }

    @Test
    void ownMessagesAreKept() {
        AnalyzeException e = assertThrows(AnalyzeException.class, () -> ModelCalls.call(() -> {
            throw new AnalyzeException("模型输出超长被截断，请重拍一张范围小一些的照片");
        }));
        assertEquals("模型输出超长被截断，请重拍一张范围小一些的照片", e.getMessage());
    }
}
