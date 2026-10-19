package io.github.xiaowuiot.inspection.inspection;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import io.github.xiaowuiot.inspection.hazard.HazardRepository;
import io.github.xiaowuiot.inspection.hazard.HazardStatus;
import io.github.xiaowuiot.inspection.hazard.HazardView;
import io.github.xiaowuiot.inspection.photo.PhotoStore;
import io.github.xiaowuiot.inspection.security.CurrentUser;
import io.github.xiaowuiot.inspection.user.Permission;
import io.github.xiaowuiot.inspection.user.User;

@RestController
@RequestMapping("/api")
public class InspectionController {

    private final InspectionService service;
    private final InspectionRepository inspections;
    private final HazardRepository hazards;
    private final PhotoStore photos;
    private final CurrentUser current;

    public InspectionController(InspectionService service, InspectionRepository inspections, HazardRepository hazards,
                                PhotoStore photos, CurrentUser current) {
        this.service = service;
        this.inspections = inspections;
        this.hazards = hazards;
        this.photos = photos;
        this.current = current;
    }

    /** 上传一张照片识别。照片在手机端已经压缩到长边 1568 像素以内 */
    @PostMapping(value = "/inspections", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('PERM_INSPECT')")
    public InspectionView inspect(@RequestParam("image") MultipartFile image,
                                  @RequestParam("sceneId") String sceneId,
                                  @RequestParam(value = "location", defaultValue = "") String location,
                                  @RequestParam(value = "note", defaultValue = "") String note) throws IOException {
        if (image.isEmpty()) {
            throw ApiException.badRequest("没有收到照片");
        }
        return service.inspect(current.get().getId(), image.getBytes(), sceneId, location, note);
    }

    /**
     * 识别记录：mine=true 只看自己的；有"查看全部"权限才能看所有人的。已删除的记录只有管理员在"全部"里能看到。
     */
    @GetMapping("/inspections")
    @Transactional(readOnly = true)
    public PageResult<InspectionView> list(@RequestParam(defaultValue = "true") boolean mine,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        User user = current.get();
        PageRequest pr = PageRequest.of(page, Math.min(size, 100));
        Page<Inspection> result;
        if (!mine && user.has(Permission.VIEW_ALL)) {
            result = inspections.search(user.has(Permission.ADMIN), pr);
        } else {
            result = inspections.findByUserIdAndDeletedFalseOrderByIdDesc(user.getId(), pr);
        }
        return PageResult.of(result, i -> InspectionView.of(i, null));
    }

    @GetMapping("/inspections/{id}")
    @Transactional(readOnly = true)
    public InspectionView get(@PathVariable Long id) {
        User user = current.get();
        Inspection i = find(id, user);
        boolean mine = i.getUser().getId().equals(user.getId());
        boolean reviewer = user.has(Permission.VIEW_ALL) || user.has(Permission.HAZARD_REVIEW);
        if (!mine && !reviewer) {
            throw ApiException.forbidden("只能查看自己的识别记录");
        }
        // 别人还没上报的草稿不给审核员看
        return InspectionView.of(i, hazards.findByInspectionIdOrderById(id).stream()
                .filter(h -> mine || !h.getStatus().isPrivate())
                .map(HazardView::of).toList());
    }

    /**
     * 删除自己的识别记录。假删除：自己和其他人都看不到了，管理员在后台还能看到。
     * 还没上报的隐患一起标为已忽略；已经上报的隐患不受影响，继续走审核。
     */
    @DeleteMapping("/inspections/{id}")
    @Transactional
    public Map<String, Boolean> delete(@PathVariable Long id) {
        User user = current.get();
        Inspection i = find(id, user);
        if (!i.getUser().getId().equals(user.getId())) {
            throw ApiException.forbidden("只能删除自己的识别记录");
        }
        i.markDeleted();
        hazards.findByInspectionIdOrderById(id).stream()
                .filter(h -> h.getStatus() == HazardStatus.DRAFT)
                .forEach(h -> h.setStatus(HazardStatus.DISCARDED));
        return Map.of("ok", true);
    }

    /** 已删除的记录对管理员以外的人当作不存在 */
    private Inspection find(Long id, User user) {
        Inspection i = inspections.findById(id).orElseThrow(() -> ApiException.notFound("识别记录不存在"));
        if (i.isDeleted() && !user.has(Permission.ADMIN)) {
            throw ApiException.notFound("识别记录不存在或已删除");
        }
        return i;
    }

    public record SubmitRequest(List<Long> hazardIds) {
    }

    /** 确认识别结果：勾选的隐患上报审核，其余忽略；hazardIds 为空表示都不上报 */
    @PostMapping("/inspections/{id}/submit")
    @Transactional
    public InspectionView submit(@PathVariable Long id, @RequestBody SubmitRequest body) {
        User user = current.get();
        find(id, user);
        List<Long> ids = body.hazardIds() == null ? List.of() : body.hazardIds();
        service.submit(user, id, ids);
        Inspection i = inspections.findById(id).orElseThrow();
        return InspectionView.of(i, hazards.findByInspectionIdOrderById(id).stream().map(HazardView::of).toList());
    }

    @GetMapping("/photos/{month}/{name}")
    public ResponseEntity<Resource> photo(@PathVariable String month, @PathVariable String name) {
        current.id();
        Path path = photos.resolve(month + "/" + name);
        if (!Files.exists(path)) {
            throw ApiException.notFound("照片不存在");
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePrivate())
                .body(new FileSystemResource(path));
    }
}
