package io.github.xiaowuiot.inspection.user;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.xiaowuiot.inspection.common.ApiException;
import io.github.xiaowuiot.inspection.config.InspectionProperties;
import io.github.xiaowuiot.inspection.security.AuthUser;
import io.github.xiaowuiot.inspection.security.CurrentUser;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authManager;
    private final SecurityContextRepository contextRepo;
    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    private final CurrentUser current;
    private final InspectionProperties props;

    public AuthController(AuthenticationManager authManager, SecurityContextRepository contextRepo,
                          UserRepository users, RoleRepository roles, PasswordEncoder encoder,
                          CurrentUser current, InspectionProperties props) {
        this.props = props;
        this.authManager = authManager;
        this.contextRepo = contextRepo;
        this.users = users;
        this.roles = roles;
        this.encoder = encoder;
        this.current = current;
    }

    public record LoginRequest(@NotBlank(message = "请输入用户名") String username,
                               @NotBlank(message = "请输入密码") String password) {
    }

    public record RegisterRequest(
            @NotBlank(message = "请输入用户名")
            @Pattern(regexp = "[A-Za-z0-9_]{3,20}", message = "用户名为 3–20 位字母、数字或下划线") String username,
            @NotBlank(message = "请输入密码") @Size(min = 6, max = 40, message = "密码长度 6–40 位") String password,
            @NotBlank(message = "请输入姓名") @Size(max = 20, message = "姓名最多 20 个字") String displayName,
            @Pattern(regexp = "^$|1\\d{10}", message = "手机号格式不正确") String phone) {
    }

    public record PasswordRequest(@NotBlank(message = "请输入原密码") String oldPassword,
                                  @NotBlank(message = "请输入新密码") @Size(min = 6, max = 40, message = "新密码长度 6–40 位") String newPassword) {
    }

    public record Me(UserView user) {
    }

    /** 登录页需要的公开信息 */
    @GetMapping("/options")
    public Map<String, Object> options() {
        return Map.of("allowRegister", props.allowRegister(), "promo", props.promo());
    }

    @PostMapping("/login")
    @Transactional
    public Me login(@Valid @RequestBody LoginRequest body, HttpServletRequest req, HttpServletResponse resp) {
        Authentication auth;
        try {
            auth = authManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(body.username().strip(), body.password()));
        } catch (DisabledException e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "DISABLED", "账号已被停用，请联系系统管理员");
        } catch (AuthenticationException e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "用户名或密码错误");
        }
        // 登录成功后换一个会话 ID，防止会话固定攻击
        req.getSession(true);
        req.changeSessionId();
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
        contextRepo.saveContext(ctx, req, resp);

        User user = users.findById(((AuthUser) auth.getPrincipal()).getId()).orElseThrow();
        user.setLastLoginAt(LocalDateTime.now());
        return new Me(UserView.of(user));
    }

    @PostMapping("/register")
    @Transactional
    public Me register(@Valid @RequestBody RegisterRequest body, HttpServletRequest req, HttpServletResponse resp) {
        if (!props.allowRegister()) {
            throw ApiException.forbidden("系统暂未开放注册，请联系管理员开通账号");
        }
        if (users.existsByUsername(body.username())) {
            throw ApiException.badRequest("用户名已被占用");
        }
        User user = new User(body.username(), encoder.encode(body.password()), body.displayName().strip());
        user.setPhone(body.phone());
        user.setRoles(new java.util.HashSet<>(Set.of(roles.findByCode(Role.USER).orElseThrow())));
        users.save(user);
        return login(new LoginRequest(body.username(), body.password()), req, resp);
    }

    @PostMapping("/logout")
    public Map<String, Boolean> logout(HttpServletRequest req) {
        if (req.getSession(false) != null) {
            req.getSession(false).invalidate();
        }
        SecurityContextHolder.clearContext();
        return Map.of("ok", true);
    }

    @GetMapping("/me")
    public Me me() {
        User user = current.get();
        return new Me(UserView.of(user));
    }

    @PostMapping("/password")
    @Transactional
    public Map<String, Boolean> changePassword(@Valid @RequestBody PasswordRequest body) {
        User user = current.get();
        if (!encoder.matches(body.oldPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("原密码不正确");
        }
        user.setPasswordHash(encoder.encode(body.newPassword()));
        users.save(user);
        return Map.of("ok", true);
    }

}
