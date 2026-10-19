package io.github.xiaowuiot.inspection.audit;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * 登录记录：登录成功、登录失败、退出登录。
 */
@Entity
@Table(name = "login_logs", indexes = {
        @Index(name = "idx_login_log_user", columnList = "user_id, created_at"),
        @Index(name = "idx_login_log_time", columnList = "created_at")})
public class LoginLog {

    public enum Action {
        LOGIN, LOGIN_FAILED, LOGOUT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(length = 64, nullable = false)
    private String username;

    @Column(length = 64)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(length = 16, nullable = false)
    private Action action;

    @Column(length = 200)
    private String message;

    @Column(length = 64)
    private String ip;

    /** 手机、电脑 */
    @Column(length = 16)
    private String client;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected LoginLog() {
    }

    public LoginLog(Long userId, String username, String name, Action action, String message, RequestInfo req) {
        this.userId = userId;
        this.username = RequestInfo.cut(username, 64);
        this.name = name;
        this.action = action;
        this.message = RequestInfo.cut(message, 200);
        this.ip = req.ip();
        this.client = req.client();
        this.userAgent = req.userAgent();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getName() {
        return name;
    }

    public Action getAction() {
        return action;
    }

    public String getMessage() {
        return message;
    }

    public String getIp() {
        return ip;
    }

    public String getClient() {
        return client;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
