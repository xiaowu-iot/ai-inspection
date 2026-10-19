package io.github.xiaowuiot.inspection.user;

/**
 * 权限点。角色由若干权限组成。
 */
public enum Permission {

    INSPECT("拍照识别"),
    HAZARD_REPORT("上报隐患"),
    HAZARD_REVIEW("审核隐患"),
    VIEW_ALL("查看全部数据"),
    ADMIN("系统管理");

    private final String label;

    Permission(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Spring Security 里的权限名 */
    public String authority() {
        return "PERM_" + name();
    }
}
