package io.github.xiaowuiot.inspection.hazard;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import io.github.xiaowuiot.inspection.inspection.Inspection;
import io.github.xiaowuiot.inspection.user.User;

/**
 * 一条隐患。来源是 AI 识别（关联 Inspection）或人工上报。
 */
@Entity
@Table(name = "hazards", indexes = {@Index(columnList = "status"), @Index(columnList = "createdAt")})
public class Hazard {

    public static final String SOURCE_AI = "AI";
    public static final String SOURCE_MANUAL = "MANUAL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inspection_id")
    private Inspection inspection;

    @Column(nullable = false, length = 10)
    private String source;

    @Column(nullable = false, length = 40)
    private String sceneName;

    @Column(length = 100)
    private String location;

    @Column(nullable = false, length = 50)
    private String photoId;

    @Column(length = 20)
    private String category;

    @Column(nullable = false, length = 500)
    private String description;

    /** 一般 / 较大 / 重大 */
    @Column(nullable = false, length = 4)
    private String level;

    @Column(length = 500)
    private String advice;

    /** 画面中的大致位置 "x1,y1,x2,y2"（0–1000），人工上报时为空 */
    @Column(length = 40)
    private String box;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private HazardStatus status = HazardStatus.PENDING_REVIEW;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id")
    private User reporter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id")
    private User reviewer;

    @Column(length = 300)
    private String reviewComment;

    private LocalDateTime reviewedAt;

    private LocalDateTime createdAt = LocalDateTime.now();

    protected Hazard() {
    }

    public Hazard(String source, Inspection inspection, User reporter, String sceneName, String location,
                  String photoId, String category, String description, String level, String advice, String box) {
        this.source = source;
        this.inspection = inspection;
        this.reporter = reporter;
        this.sceneName = sceneName;
        this.location = location;
        this.photoId = photoId;
        this.category = category;
        this.description = description;
        this.level = level;
        this.advice = advice;
        this.box = box;
    }

    public void review(User reviewer, HazardStatus status, String comment) {
        this.reviewer = reviewer;
        this.status = status;
        this.reviewComment = comment;
        this.reviewedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Inspection getInspection() { return inspection; }
    public String getSource() { return source; }
    public String getSceneName() { return sceneName; }
    public String getLocation() { return location; }
    public String getPhotoId() { return photoId; }
    public String getCategory() { return category; }
    public String getDescription() { return description; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public String getAdvice() { return advice; }
    public String getBox() { return box; }
    public HazardStatus getStatus() { return status; }
    public void setStatus(HazardStatus status) { this.status = status; }
    public User getReporter() { return reporter; }
    public void setReporter(User reporter) { this.reporter = reporter; }
    public User getReviewer() { return reviewer; }
    public String getReviewComment() { return reviewComment; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
