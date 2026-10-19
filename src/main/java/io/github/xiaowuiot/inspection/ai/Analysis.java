package io.github.xiaowuiot.inspection.ai;

/**
 * 一次调用的结果加上 token 用量，用来核算成本。
 */
public record Analysis(AnalysisResult result, int inputTokens, int outputTokens) {
}
