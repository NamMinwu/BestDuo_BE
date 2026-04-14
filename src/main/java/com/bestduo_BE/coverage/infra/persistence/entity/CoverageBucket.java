package com.bestduo_BE.coverage.infra.persistence.entity;

import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.coverage.domain.model.CoverageBucketStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "coverage_bucket",
    uniqueConstraints = @UniqueConstraint(name = "uk_coverage_bucket_patch_tier", columnNames = {"patch", "tier"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class CoverageBucket {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String patch;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Tier tier;

  @Column(name = "target_match_count", nullable = false)
  private long targetMatchCount;

  @Column(name = "current_match_count", nullable = false)
  private long currentMatchCount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private CoverageBucketStatus status;

  @Column(nullable = false)
  private int priority;

  // ── SEED 진행 상태 ────────────────────────────────────────────────────────
  // MASTER/GRANDMASTER/CHALLENGER는 division이 없으므로 seedPage만 증가
  // DIAMOND 이하는 I→II→III→IV 순으로 rotation하며 페이지 소진 시 다음 division으로 이동
  @Builder.Default
  @Column(name = "seed_page", nullable = false)
  private int seedPage = 1;

  @Builder.Default
  @Column(name = "seed_division", length = 3, nullable = false)
  private String seedDivision = "I";
  // ─────────────────────────────────────────────────────────────────────────

  // ── 일일 Seed 완료 상태 ──────────────────────────────────────────────────────
  @Builder.Default
  @Column(name = "daily_seed_completed", nullable = false)
  private boolean dailySeedCompleted = false;

  @Column(name = "daily_seed_reset_at")
  private OffsetDateTime dailySeedResetAt;

  @Builder.Default
  @Column(name = "daily_seed_steps_processed", nullable = false)
  private int dailySeedStepsProcessed = 0;
  // ─────────────────────────────────────────────────────────────────────────

  @Column(name = "last_evaluated_at", nullable = false)
  private OffsetDateTime lastEvaluatedAt;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  private static final List<String> DIVISIONS = List.of("I", "II", "III", "IV");
  /** division 구분 없이 단일 리그로 운영되는 apex 티어 */
  private static final Set<Tier> APEX_TIERS = Set.of(Tier.CHALLENGER, Tier.GRANDMASTER, Tier.MASTER);

  public static CoverageBucket create(String patch, Tier tier, long targetMatchCount, int priority) {
    OffsetDateTime now = OffsetDateTime.now();
    return CoverageBucket.builder()
        .patch(patch)
        .tier(tier)
        .targetMatchCount(targetMatchCount)
        .currentMatchCount(0L)
        .status(CoverageBucketStatus.COLLECTING)
        .priority(priority)
        .lastEvaluatedAt(now)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }

  public void advanceToNextPage() {
    this.seedPage++;
    this.updatedAt = OffsetDateTime.now();
  }

  public void advanceToNextDivisionStart() {
    int idx = DIVISIONS.indexOf(this.seedDivision);
    this.seedDivision = DIVISIONS.get((idx + 1) % DIVISIONS.size());
    this.seedPage = 1;
    this.updatedAt = OffsetDateTime.now();
  }

  public void incrementDailySeedProgress() {
    this.dailySeedStepsProcessed++;
    this.updatedAt = OffsetDateTime.now();
  }

  public boolean isDailyQuotaReached(int quota) {
    return this.dailySeedStepsProcessed >= quota;
  }

  @Deprecated
  public void advanceSeedState(int maxPagesPerDivision) {
    if (APEX_TIERS.contains(this.tier)) {
      advanceToNextPage();
    } else if (this.seedPage >= maxPagesPerDivision) {
      advanceToNextDivisionStart();
    } else {
      advanceToNextPage();
    }
  }

  /** 오늘 이 bucket의 seed를 완료했음을 기록한다. */
  public void markDailySeedCompleted() {
    this.dailySeedCompleted = true;
    this.updatedAt = OffsetDateTime.now();
  }

  /**
   * 자정 경계를 넘었으면 dailySeedCompleted를 리셋하고 dailySeedResetAt을 오늘 자정으로 기록한다.
   *
   * @param lastResetAt 마지막으로 리셋된 시점 (null이면 한 번도 리셋 안 함)
   * @param today       오늘 날짜
   */
  public void resetDailySeedIfNeeded(OffsetDateTime lastResetAt, LocalDate today) {
    boolean needsReset = lastResetAt == null
        || lastResetAt.toLocalDate().isBefore(today);
    if (!needsReset) {
      return;
    }
    this.dailySeedCompleted = false;
    this.dailySeedStepsProcessed = 0;
    this.dailySeedResetAt = today.atStartOfDay().atOffset(OffsetDateTime.now().getOffset());
    this.updatedAt = OffsetDateTime.now();
  }

  public void refreshCount(long newCount) {
    if (this.currentMatchCount == newCount) {
      return; // 변경 없음 → JPA dirty check 불필요, 불필요한 UPDATE 방지
    }
    this.currentMatchCount = newCount;
    this.status = newCount >= targetMatchCount
        ? CoverageBucketStatus.SUFFICIENT
        : CoverageBucketStatus.COLLECTING;
    this.lastEvaluatedAt = OffsetDateTime.now();
    this.updatedAt = this.lastEvaluatedAt;
  }
}
