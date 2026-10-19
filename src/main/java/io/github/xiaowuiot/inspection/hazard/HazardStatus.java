package io.github.xiaowuiot.inspection.hazard;

import java.util.EnumSet;
import java.util.Set;

/**
 * 隐患状态：
 * AI 识别 → 待上报（只有拍照人自己看得到）→ 拍照人勾选上报 → 待审核 → 审核确认（已确认）或判定误报。
 * 没勾选的是"已忽略"，审核判定误报的是"误报"。人工上报的隐患直接进入待审核。
 */
public enum HazardStatus {
    DRAFT("待上报"),
    DISCARDED("已忽略"),
    PENDING_REVIEW("待审核"),
    CONFIRMED("已确认"),
    REJECTED("误报");

    /** 只有上报人自己能看到的状态 */
    public static final Set<HazardStatus> PRIVATE = EnumSet.of(DRAFT, DISCARDED);

    private final String label;

    HazardStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean isPrivate() {
        return PRIVATE.contains(this);
    }
}
