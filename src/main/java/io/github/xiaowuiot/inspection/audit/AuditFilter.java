package io.github.xiaowuiot.inspection.audit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import io.github.xiaowuiot.inspection.security.AuthUser;

/**
 * 登录用户调用写接口（POST / PUT / DELETE / PATCH）时记一条操作记录：谁、什么时候、在哪个端、做了什么、成功还是失败。
 * 放在演示保护和权限判断之前，被拒绝的操作也会记下来。
 */
public class AuditFilter extends OncePerRequestFilter {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final AuditService audit;

    public AuditFilter(AuditService audit) {
        this.audit = audit;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod();
        String path = path(request);
        return "GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)
                || !path.startsWith("/api/") || AuditCatalog.skip(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthUser user)) {
            chain.doFilter(req, resp);
            return;
        }
        ContentCachingResponseWrapper wrapped = new ContentCachingResponseWrapper(resp);
        long start = System.currentTimeMillis();
        int status = 500;
        try {
            chain.doFilter(req, wrapped);
            status = wrapped.getStatus();
        } finally {
            String message = status >= 400 ? errorMessage(wrapped) : null;
            wrapped.copyBodyToResponse();
            audit.operation(user.getId(), user.getUsername(), req.getMethod(), path(req), status, message,
                    System.currentTimeMillis() - start, req);
        }
    }

    private static String errorMessage(ContentCachingResponseWrapper resp) {
        try {
            JsonNode n = JSON.readTree(new String(resp.getContentAsByteArray(), StandardCharsets.UTF_8));
            return n.path("message").asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    private static String path(HttpServletRequest req) {
        return req.getRequestURI().substring(req.getContextPath().length());
    }
}
