package io.github.xiaowuiot.inspection.ai;

import java.util.List;

/**
 * 模型返回的分析结果。字段名和提示词里约定的 JSON 一一对应。
 */
public record AnalysisResult(String scene, List<Hazard> hazards, List<String> cannotJudge, String confidence) {

    /**
     * @param box 隐患在画面中的大致位置 [x1, y1, x2, y2]，按 0–1000 归一化；模型给不出时为空
     */
    public record Hazard(String category, String description, String level, String advice, List<Integer> box) {
    }

    public AnalysisResult {
        hazards = hazards == null ? List.of() : hazards;
        cannotJudge = cannotJudge == null ? List.of() : cannotJudge;
    }
}
