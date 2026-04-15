package com.bestduo_BE.coverage.infra.persistence.entity;

import com.bestduo_BE.common.domain.model.Tier;
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
  @Column(name = "daily_seed_reset_at")
  private OffsetDateTime dailySeedResetAt;

  /** 오늘 처리한 페이지 수 (자정 리셋). 사이클 위치(seedPage/seedDivision)와 독립적으로 초기화된다. */
  @Builder.Default
  @Column(name = "daily_pages_processed", nullable = false)
  private int dailyPagesProcessed = 0;

  /** 사이클 완주 횟수 (IV → I wrap 발생마다 증가). */
  @Builder.Default
  @Column(name = "daily_cycle_count", nullable = false)
  private int dailyCycleCount = 0;
  // ─────────────────────────────────────────────────────────────────────────

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  private static final List<String> DIVISIONS = List.of("I", "II", "III", "IV");
  /** division 구분 없이 단일 리그로 운영되는 apex 티어 */
  private static final Set<Tier> APEX_TIERS = Set.of(Tier.CHALLENGER, Tier.GRANDMASTER, Tier.MASTER);

  public static CoverageBucket create(String patch, Tier tier) {
    OffsetDateTime now = OffsetDateTime.now();
    return CoverageBucket.builder()
        .patch(patch)
        .tier(tier)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }

  /**
   * SEED WorkItem 발행 직후 호출. 다음 SEED가 새 페이지/division을 사용하도록 상태를 전진시킨다.
   *
   * <p>apex 티어(MASTER/GRANDMASTER/CHALLENGER)는 Riot league-v4가 division 없이 단일 리그를
   * 제공하므로 페이지만 증가한다.
   * 그 외 티어는 페이지가 maxPagesPerDivision(safety cap)에 도달하면 {@link #advanceToNextDivision()}을 통해
   * 다음 division으로 이동한다. 정상 경로에서는 빈 응답이 먼저 division 전환을 트리거한다.
   */
  public void advanceSeedState(int maxPagesPerDivision) {
    if (APEX_TIERS.contains(this.tier)) {
      this.seedPage++;
    } else if (this.seedPage >= maxPagesPerDivision) {
      advanceToNextDivision();
    } else {
      this.seedPage++;
    }
    this.updatedAt = OffsetDateTime.now();
  }

  /**
   * 빈 페이지 응답 시 호출. 현재 division이 소진됐으므로 다음 division의 1페이지로 이동.
   * IV → I wrap 발생 시 {@code dailyCycleCount}를 증가시킨다.
   */
  public void advanceToNextDivision() {
    int idx = DIVISIONS.indexOf(this.seedDivision);
    boolean cycleCompleted = (idx == DIVISIONS.size() - 1);
    this.seedDivision = DIVISIONS.get((idx + 1) % DIVISIONS.size());
    this.seedPage = 1;
    if (cycleCompleted) {
      this.dailyCycleCount++;
    }
    this.updatedAt = OffsetDateTime.now();
  }

  /** 오늘 처리량을 1 증가시킨다. 빈 응답 포함 항상 호출해야 한다. */
  public void incrementDailyPagesProcessed() {
    this.dailyPagesProcessed++;
    this.updatedAt = OffsetDateTime.now();
  }

  /**
   * 오늘 처리 가능한 quota가 남아있으면 true.
   *
   * @param quota 하루 최대 처리 페이지 수
   */
  public boolean hasRemainingDailyQuota(int quota) {
    return this.dailyPagesProcessed < quota;
  }


  /**
   * 자정 경계를 넘었으면 {@code dailyPagesProcessed}를 리셋하고
   * {@code dailySeedResetAt}을 오늘 자정으로 기록한다.
   * {@code seedPage} / {@code seedDivision}은 사이클 위치를 유지하기 위해 변경하지 않는다.
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
    this.dailyPagesProcessed = 0;
    this.dailySeedResetAt = today.atStartOfDay().atOffset(OffsetDateTime.now().getOffset());
    this.updatedAt = OffsetDateTime.now();
  }

}
