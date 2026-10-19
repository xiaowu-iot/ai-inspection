package io.github.xiaowuiot.inspection.ai;

import java.util.List;

import io.github.xiaowuiot.inspection.scene.Scene;

public interface HazardAnalyzer {

    /** 发给模型的一轮消息。role 是 user 或 assistant；image 是这一轮附的图，没有时为空 */
    record Turn(String role, String text, byte[] image) {
        public Turn(String role, String text) {
            this(role, text, null);
        }
    }

    /**
     * 分析一张照片。一次只传一张图：一次传多张时模型容易把编号对错。
     *
     * @param note  拍照人选填的补充说明，没有时为空
     */
    Analysis analyze(byte[] jpeg, Scene scene, String note);

    /** 页面上显示用，比如模型名或 "演示数据" */
    String name();
}
