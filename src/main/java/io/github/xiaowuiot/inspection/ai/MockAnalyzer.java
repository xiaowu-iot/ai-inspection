package io.github.xiaowuiot.inspection.ai;

import java.util.List;

import io.github.xiaowuiot.inspection.scene.Scene;

/**
 * 没配置模型时使用：不看照片，按场景返回固定的示例结果，只用来演示界面和流程。
 */
public class MockAnalyzer implements HazardAnalyzer {

    @Override
    public Analysis analyze(byte[] jpeg, Scene scene, String note) {
        sleepQuietly();
        return new Analysis(sample(scene), 0, 0);
    }

    private static AnalysisResult sample(Scene scene) {
        return switch (scene.id()) {
            case "power-room" -> new AnalysisResult(
                    "【演示数据】配电房，墙上有两个配电箱",
                    List.of(
                            new AnalysisResult.Hazard("电气", "右侧配电箱门敞开，内部导线外露可触及", "较大",
                                    "关闭并锁好箱门，空位加装挡板", List.of(550, 150, 900, 600)),
                            new AnalysisResult.Hazard("消防", "配电箱前堆放纸箱等可燃物", "一般",
                                    "清除箱前杂物，保持 1 米以上操作通道", List.of(400, 650, 950, 980))),
                    List.of("地面是否铺设绝缘垫：照片未拍到地面"),
                    "中");
            default -> new AnalysisResult(
                    "【演示数据】" + scene.name() + "现场",
                    List.of(
                            new AnalysisResult.Hazard("消防", "安全出口前堆放货物托盘，通道被占用", "较大",
                                    "立即清理出口前物品，划设禁止堆放区", List.of(250, 300, 700, 900)),
                            new AnalysisResult.Hazard("人员防护", "画面右侧作业人员未戴安全帽", "一般",
                                    "进入作业区必须佩戴安全帽", List.of(720, 150, 900, 600))),
                    List.of("灭火器压力和有效期：需要近距离补拍"),
                    "中");
        };
    }

    @Override
    public String name() {
        return "演示数据（未配置模型）";
    }

    private static void sleepQuietly() {
        try {
            // 模拟模型思考时间，让演示时的加载状态看得见
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
