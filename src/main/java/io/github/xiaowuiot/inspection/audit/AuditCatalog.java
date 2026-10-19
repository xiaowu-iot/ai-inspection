package io.github.xiaowuiot.inspection.audit;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 接口和操作名称的对照：哪些写接口要记操作记录、属于哪个模块、叫什么操作。
 * 路径里的 {id} 记成操作对象 ID。没登记的写接口按"其他操作"记下，不会漏。
 */
public final class AuditCatalog {

    public record Entry(String module, String action) {
    }

    private record Rule(String method, Pattern path, Entry entry) {
    }

    /** 不算操作的写接口：登录、注册、退出单独记登录记录 */
    private static final List<Pattern> SKIP = List.of(
            Pattern.compile("/api/auth/(login|logout|register)"));

    private static final List<Rule> RULES = new ArrayList<>();

    static {
        String ID = "(\\d+)";
        add("POST", "/api/inspections", "巡检识别", "拍照识别");
        add("POST", "/api/inspections/" + ID + "/submit", "巡检识别", "上报识别出的隐患");
        add("DELETE", "/api/inspections/" + ID, "巡检识别", "删除识别记录");
        add("POST", "/api/hazards", "隐患", "人工上报隐患");
        add("POST", "/api/hazards/" + ID + "/review", "隐患", "审核确认隐患");
        add("POST", "/api/hazards/" + ID + "/false-positive", "隐患", "判为误报");
        add("POST", "/api/auth/password", "账号", "修改密码");
    }

    private static final Entry OTHER = new Entry("其他", "其他操作");

    private AuditCatalog() {
    }

    private static void add(String method, String regex, String module, String action) {
        RULES.add(new Rule(method, Pattern.compile(regex), new Entry(module, action)));
    }

    /** 要不要记操作记录 */
    public static boolean skip(String path) {
        return SKIP.stream().anyMatch(p -> p.matcher(path).matches());
    }

    public record Match(Entry entry, Long targetId) {
    }

    public static Match match(String method, String path) {
        for (Rule r : RULES) {
            if (!r.method().equals(method)) {
                continue;
            }
            Matcher m = r.path().matcher(path);
            if (m.matches()) {
                Long id = m.groupCount() > 0 ? Long.valueOf(m.group(1)) : null;
                return new Match(r.entry(), id);
            }
        }
        return new Match(OTHER, null);
    }

    /** 筛选用的模块列表 */
    public static Set<String> modules() {
        Set<String> s = new LinkedHashSet<>();
        RULES.forEach(r -> s.add(r.entry().module()));
        s.add(OTHER.module());
        return s;
    }
}
