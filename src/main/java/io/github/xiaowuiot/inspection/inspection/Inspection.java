package io.github.xiaowuiot.inspection.inspection;

import java.time.LocalDateTime;

import org.hibernate.annotations.ColumnDefault;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import io.github.xiaowuiot.inspection.user.User;

/**
 * 一次拍照识别。识别出的每条隐患另存为 Hazard。
 */
@Entity
@Table(name = "inspections", indexes = @Index(columnList = "createdAt"))
public class Inspection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 40)
    private String sceneId;

    @Column(nullable = false, length = 40)
    private String sceneName;

    @Column(length = 100)
    private String location;

    @Column(nullable = false, length = 50)
    private String photoId;

    /** 拍照人选填的补充说明 */
    @Column(length = 300)
    private String note;

    @Column(length = 60)
    private String analyzer;

    /** 模型对画面的一句话描述 */
    @Column(length = 500)
    private String sceneSummary;

    /** 模型说看不清、需要补拍的项，按行分隔 */
    @Lob
    private String cannotJudge;

    @Column(length = 10)
    private String confidence;

    private int hazardCount;

    private long durationMs;
    private int inputTokens;
    private int outputTokens;

    /** 识别失败时的原因 */
    @Column(length = 1000)
    private String error;

    // 后加的列要带默认值，否则给已有数据的表加列会失败
    /** 拍照人自己删除的记录：假删除，只有管理员还能看到 */
    @ColumnDefault("false")
    private boolean deleted;

    private LocalDateTime deletedAt;

    private LocalDateTime createdAt = LocalDateTime.now();

    protected Inspection() {
    }

    public Inspection(User user, String sceneId, String sceneName, String location, String photoId) {
        this.user = user;
        this.sceneId = sceneId;
        this.sceneName = sceneName;
        this.location = location;
        this.photoId = photoId;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getSceneId() { return sceneId; }
    public String getSceneName() { return sceneName; }
    public String getLocation() { return location; }
    public String getPhotoId() { return photoId; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getAnalyzer() { return analyzer; }
    public void setAnalyzer(String analyzer) { this.analyzer = analyzer; }
    public String getSceneSummary() { return sceneSummary; }
    public void setSceneSummary(String sceneSummary) { this.sceneSummary = sceneSummary; }
    public String getCannotJudge() { return cannotJudge; }
    public void setCannotJudge(String cannotJudge) { this.cannotJudge = cannotJudge; }
    public String getConfidence() { return confidence; }
    public void setConfidence(String confidence) { this.confidence = confidence; }
    public int getHazardCount() { return hazardCount; }
    public void setHazardCount(int hazardCount) { this.hazardCount = hazardCount; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    public int getInputTokens() { return inputTokens; }
    public void setInputTokens(int inputTokens) { this.inputTokens = inputTokens; }
    public int getOutputTokens() { return outputTokens; }
    public void setOutputTokens(int outputTokens) { this.outputTokens = outputTokens; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public boolean isDeleted() { return deleted; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void markDeleted() {
        this.deleted = true;
        this.deletedAt = LocalDateTime.now();
    }
}
