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
        // seedPage=1, seedDivision="I" 는 @Builder.Default로 자동 적용
        .lastEvaluatedAt(now)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }

  /**
   * SEED WorkItem 발행 직후 호출. 다음 SEED가 새 페이지/division을 사용하도록 상태를 전진시킨다.
   *
   * <p>apex 티어(MASTER/GRANDMASTER/CHALLENGER)는 Riot league-v4가 division 없이 단일 리그를
   * 제공하므로 페이지만 증가한다.
   * 그 외 티어는 페이지가 maxPagesPerDivision에 도달하면 다음 division(I→II→III→IV→I)으로 rotation.
   */
  public void advanceSeedState(int maxPagesPerDivision) {
    if (APEX_TIERS.contains(this.tier)) {
      this.seedPage++;
    } else if (this.seedPage >= maxPagesPerDivision) {
      int idx = DIVISIONS.indexOf(this.seedDivision);
      this.seedDivision = DIVISIONS.get((idx + 1) % DIVISIONS.size());
      this.seedPage = 1;
    } else {
      this.seedPage++;
    }
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
