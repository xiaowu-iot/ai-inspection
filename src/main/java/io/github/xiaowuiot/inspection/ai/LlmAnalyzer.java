package io.github.xiaowuiot.inspection.ai;

import java.util.List;

import io.github.xiaowuiot.inspection.scene.Scene;

/**
 * 真实模型的公共流程：拼提示词、解析结果。各家接口只需要实现 complete。
 * 每次调用都经过 {@link ModelCalls}：网络偶发断连自动重试一次，失败时给用户看中文提示，原始错误只写日志。
 */
public abstract class LlmAnalyzer implements HazardAnalyzer {

    /** 一次模型调用的文本结果和用量 */
    protected record Completion(String text, int inputTokens, int outputTokens) {
    }

    /**
     * 调一次模型。turns 以 user 开头、user/assistant 交替。原始照片附在第一条 user 消息上；
     * 其他 user 消息如果带了 image（圈图或补拍），也要一起发。
     */
    protected abstract Completion complete(byte[] jpeg, List<Turn> turns);

    @Override
    public Analysis analyze(byte[] jpeg, Scene scene, String note) {
        Completion c = ModelCalls.call(() -> complete(jpeg, List.of(new Turn("user", Prompts.forScene(scene, note)))));
        return new Analysis(ResultParser.parse(c.text()), c.inputTokens(), c.outputTokens());
    }

}
