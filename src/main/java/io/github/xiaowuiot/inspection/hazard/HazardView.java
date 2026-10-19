package io.github.xiaowuiot.inspection.hazard;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import io.github.xiaowuiot.inspection.user.UserView;

public record HazardView(Long id, String source, Long inspectionId, String sceneName, String location, String photoId,
                         String category, String description, String level, String advice, List<Integer> box,
                         String status, String statusLabel, UserView.Brief reporter, UserView.Brief reviewer,
                         String reviewComment, LocalDateTime reviewedAt, LocalDateTime createdAt) {

    public static HazardView of(Hazard h) {
        return new HazardView(h.getId(), h.getSource(),
                h.getInspection() == null ? null : h.getInspection().getId(),
                h.getSceneName(), h.getLocation(), h.getPhotoId(), h.getCategory(), h.getDescription(),
                h.getLevel(), h.getAdvice(), parseBox(h.getBox()),
                h.getStatus().name(), h.getStatus().label(),
                UserView.Brief.of(h.getReporter()), UserView.Brief.of(h.getReviewer()),
                h.getReviewComment(), h.getReviewedAt(), h.getCreatedAt());
    }

    static List<Integer> parseBox(String box) {
        if (box == null || box.isBlank()) {
            return null;
        }
        try {
            return Arrays.stream(box.split(",")).map(String::trim).map(Integer::valueOf).toList();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 模型给的坐标规整到 0–1000 再存，给不出合法框就不存 */
    public static String formatBox(List<Integer> box) {
        if (box == null || box.size() != 4) {
            return null;
        }
        int[] v = box.stream().mapToInt(i -> Math.max(0, Math.min(1000, i == null ? 0 : i))).toArray();
        if (v[2] <= v[0] || v[3] <= v[1]) {
            return null;
        }
        return v[0] + "," + v[1] + "," + v[2] + "," + v[3];
    }
}
