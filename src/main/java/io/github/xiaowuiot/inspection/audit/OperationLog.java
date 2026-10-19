package io.github.xiaowuiot.inspection.audit;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * 操作记录：登录用户的每一次新增、修改、删除和业务操作（派单、审核、下发命令……），成功失败都记。
 */
@Entity
@Table(name = "operation_logs", indexes = {
        @Index(name = "idx_op_log_user", columnList = "user_id, created_at"),
        @Index(name = "idx_op_log_time", columnList = "created_at")})
public class OperationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(length = 64, nullable = false)
    private String username;

    @Column(length = 64)
    private String name;

    /** 模块，如 预警处置、设备运维 */
    @Column(length = 32, nullable = false)
    private String module;

    /** 操作，如 派单、删除设备 */
    @Column(length = 64, nullable = false)
    private String action;

    /** 操作对象的 ID（接口路径里的数字） */
    @Column(name = "target_id")
    private Long targetId;

    @Column(length = 8, nullable = false)
    private String method;

    @Column(length = 200, nullable = false)
    private String path;

    private int status;

    private boolean success;

    /** 失败原因 */
    @Column(length = 200)
    private String message;

    @Column(name = "duration_ms")
    private long durationMs;

    @Column(length = 64)
    private String ip;

    @Column(length = 16)
    private String client;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected OperationLog() {
    }

    public OperationLog(Long userId, String username, String name, AuditCatalog.Entry entry, Long targetId,
                        String method, String path, int status, String message, long durationMs, RequestInfo req) {
        this.userId = userId;
        this.username = RequestInfo.cut(username, 64);
        this.name = name;
        this.module = entry.module();
        this.action = entry.action();
        this.targetId = targetId;
        this.method = method;
        this.path = RequestInfo.cut(path, 200);
        this.status = status;
        this.success = status < 400;
        this.message = success ? null : RequestInfo.cut(message, 200);
        this.durationMs = durationMs;
        this.ip = req.ip();
        this.client = req.client();
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

    public String getModule() {
        return module;
    }

    public String getAction() {
        return action;
    }

    public Long getTargetId() {
        return targetId;
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public int getStatus() {
        return status;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public String getIp() {
        return ip;
    }

    public String getClient() {
        return client;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
