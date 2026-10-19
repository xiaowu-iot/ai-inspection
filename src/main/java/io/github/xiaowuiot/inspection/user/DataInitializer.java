package io.github.xiaowuiot.inspection.user;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.xiaowuiot.inspection.config.InspectionProperties;

import static io.github.xiaowuiot.inspection.user.Permission.*;

/**
 * 首次启动时建好内置角色和管理员账号。
 * 管理员初始密码随机生成，写在 data/initial-admin.txt 里，登录后请立即修改密码并删除这个文件。
 */
@Component
@Order(10)
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final RoleRepository roles;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final InspectionProperties props;

    public DataInitializer(RoleRepository roles, UserRepository users, PasswordEncoder encoder, InspectionProperties props) {
        this.roles = roles;
        this.users = users;
        this.encoder = encoder;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        Role admin = ensureRole(Role.ADMIN, "系统管理员", "拥有全部权限", EnumSet.allOf(Permission.class));
        // 系统管理员始终拥有全部权限，以后新增的权限点也自动带上
        if (!admin.getPermissions().containsAll(EnumSet.allOf(Permission.class))) {
            admin.setPermissions(EnumSet.allOf(Permission.class));
        }
        ensureRole(Role.SAFETY_MANAGER, "安全管理员", "拍照识别、审核隐患、查看全部",
                EnumSet.of(INSPECT, HAZARD_REPORT, HAZARD_REVIEW, VIEW_ALL));
        ensureRole(Role.USER, "巡检员", "拍照识别、上报隐患；自己注册的用户默认是这个角色",
                EnumSet.of(INSPECT, HAZARD_REPORT));

        if (users.findByUsername("admin").isEmpty()) {
            String password = randomPassword();
            User user = new User("admin", encoder.encode(password), "系统管理员");
            user.setRoles(new HashSet<>(Set.of(admin)));
            users.save(user);
            Path file = Path.of(props.dataDir()).toAbsolutePath().normalize().resolve("initial-admin.txt");
            Files.createDirectories(file.getParent());
            Files.writeString(file, "管理员账号：admin\n初始密码：" + password
                    + "\n\n登录后请在“我的 → 修改密码”里改掉，并删除这个文件。\n", StandardCharsets.UTF_8);
            log.warn("已创建管理员账号 admin，初始密码见 {}", file);
        }
    }

    private Role ensureRole(String code, String name, String description, Set<Permission> permissions) {
        return roles.findByCode(code).orElseGet(() -> roles.save(new Role(code, name, description, true, permissions)));
    }

    private static String randomPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
        SecureRandom r = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            sb.append(chars.charAt(r.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
