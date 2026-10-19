package io.github.xiaowuiot.inspection.inspection;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import io.github.xiaowuiot.inspection.hazard.HazardView;
import io.github.xiaowuiot.inspection.user.UserView;

public record InspectionView(Long id, UserView.Brief user, String sceneId, String sceneName, String location, String note,
                             String photoId, String analyzer, String sceneSummary, List<String> cannotJudge,
                             String confidence, int hazardCount, long durationMs,
                             int inputTokens, int outputTokens, String error, LocalDateTime createdAt,
                             boolean deleted, List<HazardView> hazards) {

    public static InspectionView of(Inspection i, List<HazardView> hazards) {
        List<String> cj = i.getCannotJudge() == null || i.getCannotJudge().isBlank()
                ? List.of() : Arrays.asList(i.getCannotJudge().split("\n"));
        return new InspectionView(i.getId(), UserView.Brief.of(i.getUser()), i.getSceneId(), i.getSceneName(),
                i.getLocation(), i.getNote(), i.getPhotoId(), i.getAnalyzer(), i.getSceneSummary(), cj, i.getConfidence(),
                i.getHazardCount(), i.getDurationMs(),
                i.getInputTokens(), i.getOutputTokens(), i.getError(), i.getCreatedAt(), i.isDeleted(), hazards);
    }
}
