package io.github.xiaowuiot.inspection.ai;

import java.util.stream.Collectors;

import io.github.xiaowuiot.inspection.scene.Scene;

final class Prompts {

    /** 中文 JSON 很占 token，2000 时实测有隐患较多的照片被截断 */
    static final int MAX_OUTPUT_TOKENS = 4096;

    private Prompts() {
    }

    static String forScene(Scene scene, String note) {
        String checklist = scene.checklist().stream()
                .map(item -> "- " + item)
                .collect(Collectors.joining("\n"));
        // 拍照人的补充说明只作为线索：说的东西画面里看不到，就不能当成隐患
        String photoNote = note == null || note.isBlank() ? "" : """

                拍照人的补充说明（帮助你理解画面，判断仍以照片里实际看到的为准）：
                「%s」
                """.formatted(note.strip());
        return """
                你是安全巡检助手。安全员在「%s」现场拍照上传，请找出照片里的安全隐患。

                本场景的检查清单：
                %s

                通用检查维度：人员防护、消防、电气、物料与环境、设备与车辆、环卫专项。
                %s
                规则：
                1. 只根据画面里实际能看到的内容判断，不要猜测画面外的情况。
                2. 不要为了凑数而报隐患，误报和漏报一样有害。
                3. 看不清或照片无法判断的检查项（如压力表读数、有效期、是否带电），放进 cannotJudge，并说明需要怎么补拍。
                4. level 只能是：一般、较大、重大。安全出口被堵、带电部位裸露可触及、人员处于设备危险区，至少是较大。
                5. 写得简短，现场人员要在手机上看：最多列 6 条最重要的隐患，同类问题合并成一条；
                   scene 不超过 40 字，description 和 advice 各不超过 60 字，cannotJudge 最多 3 条、每条不超过 50 字。
                6. box 是隐患在画面中的大致位置 [x1, y1, x2, y2]，以图片左上角为原点，宽高都按 0–1000 归一化。

                只输出一个 JSON 对象，不要输出其他文字或代码块标记：
                {"scene":"一句话描述画面","hazards":[{"category":"维度","description":"具体问题和在画面中的位置","level":"一般","advice":"整改建议","box":[x1,y1,x2,y2]}],"cannotJudge":["无法判断的检查项及补拍建议"],"confidence":"高/中/低"}
                没有发现隐患时 hazards 为空数组。
                """.formatted(scene.name(), checklist, photoNote);
    }

}
