package io.github.xiaowuiot.inspection.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import io.github.xiaowuiot.inspection.common.ApiException;
import io.github.xiaowuiot.inspection.user.User;
import io.github.xiaowuiot.inspection.user.UserRepository;

/**
 * 取当前登录用户。每次都从数据库读一遍，这样禁用账号、改角色能立刻生效。
 */
@Component
public class CurrentUser {

    private final UserRepository users;

    public CurrentUser(UserRepository users) {
        this.users = users;
    }

    public Long id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthUser u)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录");
        }
        return u.getId();
    }

    public User get() {
        User user = users.findById(id())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录"));
        if (!user.isEnabled()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "DISABLED", "账号已被停用，请联系系统管理员");
        }
        return user;
    }
}
