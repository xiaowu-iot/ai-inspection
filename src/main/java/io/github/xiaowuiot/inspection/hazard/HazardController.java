package io.github.xiaowuiot.inspection.hazard;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.github.xiaowuiot.inspection.common.ApiException;
import io.github.xiaowuiot.inspection.common.PageResult;
import io.github.xiaowuiot.inspection.photo.PhotoStore;
import io.github.xiaowuiot.inspection.scene.Scene;
import io.github.xiaowuiot.inspection.scene.SceneCatalog;
import io.github.xiaowuiot.inspection.security.CurrentUser;
import io.github.xiaowuiot.inspection.user.Permission;
import io.github.xiaowuiot.inspection.user.User;

@RestController
@RequestMapping("/api/hazards")
public class HazardController {

    private static final List<String> LEVELS = List.of("一般", "较大", "重大");

    private final HazardRepository hazards;
    private final SceneCatalog scenes;
    private final PhotoStore photos;
    private final CurrentUser current;

    public HazardController(HazardRepository hazards, SceneCatalog scenes, PhotoStore photos, CurrentUser current) {
        this.hazards = hazards;
        this.scenes = scenes;
        this.photos = photos;
        this.current = current;
    }

    public record ReviewRequest(@NotBlank(message = "请选择审核结果") String action, String level,
                                @Size(max = 300, message = "说明最多 300 字") String comment) {
    }

    /** 能看全部隐患的：审核人、有"查看全部"权限的；其他人只能看自己上报的 */
    private static boolean canSeeAll(User u) {
        return u.has(Permission.VIEW_ALL) || u.has(Permission.HAZARD_REVIEW);
    }

    @GetMapping
    @Transactional(readOnly = true)
    public PageResult<HazardView> list(@RequestParam(required = false) HazardStatus status,
                                       @RequestParam(required = false) String level,
                                       @RequestParam(defaultValue = "false") boolean mine,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        User user = current.get();
        Long reporterId = mine || !canSeeAll(user) ? user.getId() : null;
        String lv = level == null || level.isBlank() ? null : level;
        return PageResult.of(hazards.search(status, reporterId, lv, user.getId(), HazardStatus.PRIVATE,
                PageRequest.of(page, Math.min(size, 100))), HazardView::of);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public HazardView get(@PathVariable Long id) {
        User user = current.get();
        Hazard h = find(id);
        boolean mine = h.getReporter().getId().equals(user.getId());
        if (!mine && (!canSeeAll(user) || h.getStatus().isPrivate())) {
            throw ApiException.forbidden("只能查看自己上报的隐患");
        }
        return HazardView.of(h);
    }

    /** 人工上报：照片里看不出来、或者没用 AI 识别的问题，直接拍照写描述上报 */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('PERM_HAZARD_REPORT')")
    @Transactional
    public HazardView report(@RequestParam("image") MultipartFile image,
                             @RequestParam("sceneId") String sceneId,
                             @RequestParam(value = "location", defaultValue = "") String location,
                             @RequestParam(value = "category", defaultValue = "") String category,
                             @RequestParam("description") String description,
                             @RequestParam(value = "level", defaultValue = "一般") String level,
                             @RequestParam(value = "advice", defaultValue = "") String advice) throws IOException {
        User user = current.get();
        Scene scene = scenes.find(sceneId).orElseThrow(() -> ApiException.badRequest("请选择巡检场景"));
        if (description.isBlank() || description.length() > 500) {
            throw ApiException.badRequest("请填写隐患描述（500 字以内）");
        }
        if (!LEVELS.contains(level)) {
            throw ApiException.badRequest("隐患等级只能是一般、较大、重大");
        }
        String photoId = photos.save(image.getBytes());
        Hazard h = hazards.save(new Hazard(Hazard.SOURCE_MANUAL, null, user, scene.name(), limit(location, 100),
                photoId, limit(category, 20), description.strip(), level, limit(advice, 500), null));
        return HazardView.of(h);
    }

    /** 审核：确认（可以调整等级）或判定误报 */
    @PostMapping("/{id}/review")
    @PreAuthorize("hasAuthority('PERM_HAZARD_REVIEW')")
    @Transactional
    public HazardView review(@PathVariable Long id, @Valid @RequestBody ReviewRequest body) {
        User user = current.get();
        Hazard h = find(id);
        if (h.getStatus() != HazardStatus.PENDING_REVIEW) {
            throw ApiException.badRequest("这条隐患已经审核过了（" + h.getStatus().label() + "）");
        }
        switch (body.action()) {
            case "CONFIRM" -> {
                if (body.level() != null && !body.level().isBlank()) {
                    if (!LEVELS.contains(body.level())) {
                        throw ApiException.badRequest("隐患等级只能是一般、较大、重大");
                    }
                    h.setLevel(body.level());
                }
                h.review(user, HazardStatus.CONFIRMED, body.comment());
            }
            case "REJECT" -> h.review(user, HazardStatus.REJECTED, body.comment());
            default -> throw ApiException.badRequest("审核结果只能是确认或误报");
        }
        return HazardView.of(h);
    }

    /** 上报人自己觉得 AI 识别错了，可以在审核前标记误报 */
    @PostMapping("/{id}/false-positive")
    @Transactional
    public HazardView falsePositive(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        User user = current.get();
        Hazard h = find(id);
        if (!h.getReporter().getId().equals(user.getId())) {
            throw ApiException.forbidden("只能标记自己上报的隐患");
        }
        if (h.getStatus() != HazardStatus.PENDING_REVIEW) {
            throw ApiException.badRequest("这条隐患已经审核过了");
        }
        String comment = body == null ? null : body.get("comment");
        h.review(user, HazardStatus.REJECTED, comment == null || comment.isBlank() ? "上报人标记为误报" : comment);
        return HazardView.of(h);
    }

    private Hazard find(Long id) {
        return hazards.findById(id).orElseThrow(() -> ApiException.notFound("隐患不存在"));
    }

    private static String limit(String s, int max) {
        if (s == null) {
            return null;
        }
        String v = s.strip();
        return v.length() > max ? v.substring(0, max) : v;
    }
}
