package io.github.xiaowuiot.inspection.meta;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.xiaowuiot.inspection.ai.HazardAnalyzer;
import io.github.xiaowuiot.inspection.scene.SceneCatalog;

/** 手机端需要的基础数据：场景清单、隐患等级、当前用的模型 */
@RestController
@RequestMapping("/api/meta")
public class MetaController {

    private final SceneCatalog scenes;
    private final HazardAnalyzer analyzer;

    public MetaController(SceneCatalog scenes, HazardAnalyzer analyzer) {
        this.scenes = scenes;
        this.analyzer = analyzer;
    }

    @GetMapping
    public Map<String, Object> meta() {
        return Map.of("scenes", scenes.all(), "analyzer", analyzer.name(),
                "levels", List.of("一般", "较大", "重大"));
    }
}
