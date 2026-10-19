package io.github.xiaowuiot.inspection.inspection;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.xiaowuiot.inspection.ai.Analysis;
import io.github.xiaowuiot.inspection.ai.AnalysisResult;
import io.github.xiaowuiot.inspection.ai.HazardAnalyzer;
import io.github.xiaowuiot.inspection.common.ApiException;
import io.github.xiaowuiot.inspection.hazard.Hazard;
import io.github.xiaowuiot.inspection.hazard.HazardRepository;
import io.github.xiaowuiot.inspection.hazard.HazardStatus;
import io.github.xiaowuiot.inspection.hazard.HazardView;
import io.github.xiaowuiot.inspection.photo.PhotoStore;
import io.github.xiaowuiot.inspection.scene.Scene;
import io.github.xiaowuiot.inspection.scene.SceneCatalog;
import io.github.xiaowuiot.inspection.user.Permission;
import io.github.xiaowuiot.inspection.user.User;
import io.github.xiaowuiot.inspection.user.UserRepository;

@Service
public class InspectionService {

    private static final Logger log = LoggerFactory.getLogger(InspectionService.class);
    private static final List<String> LEVELS = List.of("一般", "较大", "重大");

    private final InspectionRepository inspections;
    private final HazardRepository hazards;
    private final UserRepository users;
    private final SceneCatalog scenes;
    private final HazardAnalyzer analyzer;
    private final PhotoStore photos;
    private final TransactionTemplate tx;

    public InspectionService(InspectionRepository inspections, HazardRepository hazards, UserRepository users,
                             SceneCatalog scenes, HazardAnalyzer analyzer, PhotoStore photos,
                             PlatformTransactionManager txManager) {
        this.inspections = inspections;
        this.hazards = hazards;
        this.users = users;
        this.scenes = scenes;
        this.analyzer = analyzer;
        this.photos = photos;
        this.tx = new TransactionTemplate(txManager);
    }

    /**
     * 拍照识别。调用模型要十几秒，不能包在事务里，所以先调模型、再开事务保存结果。
     */
    public InspectionView inspect(Long userId, byte[] jpeg, String sceneId, String location, String note) {
        Scene scene = scenes.find(sceneId).orElseThrow(() -> ApiException.badRequest("请选择巡检场景"));
        String photoId = photos.save(jpeg);

        long start = System.currentTimeMillis();
        Analysis analysis = null;
        String error = null;
        try {
            analysis = analyzer.analyze(jpeg, scene, note);
        } catch (RuntimeException e) {
            log.warn("识别失败 user={}：{}", userId, e.getMessage());
            error = e.getMessage();
        }
        return save(userId, scene, location, note, photoId, System.currentTimeMillis() - start, analysis, error);
    }

    private InspectionView save(Long userId, Scene scene, String location, String note, String photoId, long cost,
                                Analysis result, String err) {
        return tx.execute(s -> {
            User user = users.findById(userId).orElseThrow();
            Inspection insp = new Inspection(user, scene.id(), scene.name(), trim(location, 100), photoId);
            insp.setAnalyzer(analyzer.name());
            insp.setNote(trim(note, 300));
            insp.setDurationMs(cost);
            insp.setError(err == null ? null : trim(err, 1000));
            List<Hazard> created = new ArrayList<>();
            if (result != null) {
                AnalysisResult r = result.result();
                insp.setSceneSummary(trim(r.scene(), 500));
                insp.setCannotJudge(String.join("\n", r.cannotJudge()));
                insp.setConfidence(trim(r.confidence(), 10));
                insp.setInputTokens(result.inputTokens());
                insp.setOutputTokens(result.outputTokens());
                insp.setHazardCount(r.hazards().size());
            }
            inspections.save(insp);
            if (result != null) {
                // 识别结果先是"待上报"，只有拍照人自己看得到；确认后再上报审核
                for (AnalysisResult.Hazard h : result.result().hazards()) {
                    Hazard hz = new Hazard(Hazard.SOURCE_AI, insp, user, scene.name(), insp.getLocation(),
                            photoId, trim(h.category(), 20), trim(h.description(), 500), normalizeLevel(h.level()),
                            trim(h.advice(), 500), HazardView.formatBox(h.box()));
                    hz.setStatus(HazardStatus.DRAFT);
                    created.add(hazards.save(hz));
                }
            }
            return InspectionView.of(insp, created.stream().map(HazardView::of).toList());
        });
    }

    /**
     * 拍照人确认识别结果：勾选的隐患上报审核，没勾选的标记为"已忽略"。
     * hazardIds 为空表示整次识别都不上报。
     */
    @Transactional
    public List<Hazard> submit(User user, Long inspectionId, List<Long> hazardIds) {
        Inspection insp = inspections.findById(inspectionId).orElseThrow(() -> ApiException.notFound("识别记录不存在"));
        if (!insp.getUser().getId().equals(user.getId())) {
            throw ApiException.forbidden("只能上报自己的识别结果");
        }
        if (!hazardIds.isEmpty() && !user.has(Permission.HAZARD_REPORT)) {
            throw ApiException.forbidden("您没有上报隐患的权限");
        }
        List<Hazard> drafts = hazards.findByInspectionIdOrderById(inspectionId).stream()
                .filter(h -> h.getStatus() == HazardStatus.DRAFT).toList();
        if (drafts.isEmpty()) {
            throw ApiException.badRequest("这次识别的结果已经处理过了");
        }
        List<Long> draftIds = drafts.stream().map(Hazard::getId).toList();
        if (!draftIds.containsAll(hazardIds)) {
            throw ApiException.badRequest("只能上报本次识别中待上报的隐患");
        }
        for (Hazard h : drafts) {
            h.setStatus(hazardIds.contains(h.getId()) ? HazardStatus.PENDING_REVIEW : HazardStatus.DISCARDED);
        }
        return drafts;
    }

    static String normalizeLevel(String level) {
        return LEVELS.contains(level) ? level : "一般";
    }

    private static String trim(String s, int max) {
        if (s == null) {
            return null;
        }
        String v = s.strip();
        return v.length() > max ? v.substring(0, max) : v;
    }
}
