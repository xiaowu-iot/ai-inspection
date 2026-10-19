package io.github.xiaowuiot.inspection.audit;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.github.xiaowuiot.inspection.user.User;
import io.github.xiaowuiot.inspection.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 写登录记录和操作记录。记录失败只打日志，不影响业务；记录保留 inspection.audit.retention-days 天（默认 180）。
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final LoginLogRepository logins;
    private final OperationLogRepository operations;
    private final UserRepository users;
    private final int retentionDays;

    public AuditService(LoginLogRepository logins, OperationLogRepository operations, UserRepository users,
                        @Value("${inspection.audit.retention-days:180}") int retentionDays) {
        this.logins = logins;
        this.operations = operations;
        this.users = users;
        this.retentionDays = retentionDays;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void login(User user, String username, LoginLog.Action action, String message, HttpServletRequest req) {
        try {
            logins.save(new LoginLog(user == null ? null : user.getId(), user == null ? username : user.getUsername(),
                    user == null ? null : user.getDisplayName(), action, message, RequestInfo.of(req)));
        } catch (RuntimeException e) {
            log.warn("写登录记录失败：{}", e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void operation(Long userId, String username, String method, String path, int status, String message,
                          long durationMs, HttpServletRequest req) {
        try {
            AuditCatalog.Match m = AuditCatalog.match(method, path);
            String name = users.findById(userId).map(User::getDisplayName).orElse(null);
            operations.save(new OperationLog(userId, username, name, m.entry(), m.targetId(), method, path, status,
                    message, durationMs, RequestInfo.of(req)));
        } catch (RuntimeException e) {
            log.warn("写操作记录失败：{}", e.getMessage());
        }
    }

    @Scheduled(cron = "0 47 3 * * *")
    @Transactional
    public void cleanup() {
        if (retentionDays <= 0) {
            return;
        }
        LocalDateTime before = LocalDateTime.now().minusDays(retentionDays);
        int n = logins.deleteBefore(before) + operations.deleteBefore(before);
        if (n > 0) {
            log.info("清理了 {} 条 {} 天前的登录和操作记录", n, retentionDays);
        }
    }
}
