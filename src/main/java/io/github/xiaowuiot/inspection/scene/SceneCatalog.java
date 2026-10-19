package io.github.xiaowuiot.inspection.scene;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

@Component
public class SceneCatalog {

    private final Map<String, Scene> scenes = new LinkedHashMap<>();

    public SceneCatalog() {
        add(new Scene("workshop", "生产车间", "🔧", List.of(
                "人员防护：安全帽、护目镜、手套、口罩、工作服",
                "机械防护罩缺失、传动部位外露",
                "安全通道被占、物料堆放过高或倾斜",
                "地面油污、积水",
                "电线裸露、私拉乱接、插线板串接",
                "灭火器缺失、过期、被遮挡",
                "安全出口被堵或锁闭")));
        add(new Scene("power-room", "配电房", "⚡", List.of(
                "配电箱门敞开、盖板或挡板缺失",
                "导线裸露、接线混乱",
                "地面未铺绝缘垫",
                "堆放杂物、易燃物",
                "警示标识缺失",
                "灭火器缺失或类型不对")));
        add(new Scene("general", "通用场景", "📷", List.of(
                "人员防护",
                "消防通道与消防器材",
                "电气安全",
                "物料堆放与环境",
                "设备与车辆")));
    }

    private void add(Scene scene) {
        scenes.put(scene.id(), scene);
    }

    public List<Scene> all() {
        return List.copyOf(scenes.values());
    }

    public Optional<Scene> find(String id) {
        return Optional.ofNullable(scenes.get(id));
    }
}
