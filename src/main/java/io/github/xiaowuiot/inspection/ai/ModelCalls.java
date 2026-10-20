package io.github.xiaowuiot.inspection.ai;

import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/**
 * 模型调用的重试和报错整理。
 * <ul>
 *     <li>网络偶发断连（连接被重置、超时）、模型服务繁忙（429）或临时故障（5xx），自动再试一次</li>
 *     <li>最终失败时，原始错误（接口地址、返回内容）只写日志，给用户看的是一句中文提示，记到识别记录里的也是这句</li>
 * </ul>
 */
public final class ModelCalls {

    private static final Logger log = LoggerFactory.getLogger(ModelCalls.class);

    /** 一共调几次（第一次 + 重试） */
    static final int ATTEMPTS = 2;
    /** 重试前等多久 */
    static long retryDelayMillis = 1500;

    private ModelCalls() {
    }

    public static <T> T call(Supplier<T> call) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                return call.get();
            } catch (RuntimeException e) {
                last = e;
                if (attempt < ATTEMPTS && transient_(e)) {
                    log.warn("调用模型失败，{} 毫秒后重试：{}", retryDelayMillis, e.getMessage());
                    sleep();
                    continue;
                }
                break;
            }
        }
        log.warn("调用模型失败：{}", last.getMessage(), last);
        throw new AnalyzeException(friendly(last), last);
    }

    /**
     * 再试一次可能就好的错误：网络断连、超时，模型服务繁忙或临时故障
     */
    static boolean transient_(Throwable e) {
        if (find(e, ResourceAccessException.class) != null) {
            return true;
        }
        RestClientResponseException http = find(e, RestClientResponseException.class);
        if (http != null) {
            int code = http.getStatusCode().value();
            return code == 429 || code >= 500;
        }
        return false;
    }

    /**
     * 给用户看的提示。不带接口地址、密钥、模型返回的原文
     */
    static String friendly(Throwable e) {
        if (find(e, ResourceAccessException.class) != null) {
            return "识别服务暂时连不上，请稍后重试";
        }
        RestClientResponseException http = find(e, RestClientResponseException.class);
        if (http != null) {
            int code = http.getStatusCode().value();
            if (code == 429) {
                return "识别服务繁忙，请稍后重试";
            }
            if (code >= 500) {
                return "识别服务暂时不可用，请稍后重试";
            }
            if (code == 401 || code == 403) {
                return "识别服务配置有误，请联系管理员";
            }
            return "识别请求出错，请换一张照片再试";
        }
        // 本项目自己抛的（输出被截断、结果格式不对），本身就是中文提示，原样给用户
        if (e instanceof AnalyzeException && e.getMessage() != null) {
            return e.getMessage();
        }
        return "识别出错，请稍后重试";
    }

    private static <E extends Throwable> E find(Throwable e, Class<E> type) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return type.cast(t);
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return null;
    }

    private static void sleep() {
        try {
            Thread.sleep(retryDelayMillis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
