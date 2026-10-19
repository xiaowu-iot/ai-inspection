package io.github.xiaowuiot.inspection.audit;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 记录用的请求信息：来源 IP、客户端、浏览器。
 */
public record RequestInfo(String ip, String client, String userAgent) {

    public static RequestInfo of(HttpServletRequest req) {
        return new RequestInfo(ip(req), client(req), cut(req.getHeader("User-Agent"), 255));
    }

    /** 只有请求来自本机反向代理时才相信 X-Forwarded-For / X-Real-IP，外部直连伪造的头不认 */
    static String ip(HttpServletRequest req) {
        String remote = req.getRemoteAddr();
        boolean proxied = "127.0.0.1".equals(remote) || "0:0:0:0:0:0:0:1".equals(remote) || "::1".equals(remote);
        if (proxied) {
            String xff = req.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                return cut(xff.split(",")[0].strip(), 64);
            }
            String real = req.getHeader("X-Real-IP");
            if (real != null && !real.isBlank()) {
                return cut(real.strip(), 64);
            }
        }
        return proxied ? "127.0.0.1" : remote;
    }

    /** 按浏览器区分手机和电脑 */
    static String client(HttpServletRequest req) {
        String ua = req.getHeader("User-Agent");
        if (ua == null) {
            return "接口";
        }
        return ua.contains("Mobile") || ua.contains("Android") || ua.contains("iPhone") ? "手机" : "电脑";
    }

    static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
