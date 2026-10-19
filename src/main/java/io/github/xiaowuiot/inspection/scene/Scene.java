package io.github.xiaowuiot.inspection.scene;

import java.util.List;

/**
 * 巡检场景。每个场景带一份检查清单，会拼进提示词里，让模型按清单逐项看。
 */
public record Scene(String id, String name, String icon, List<String> checklist) {
}
