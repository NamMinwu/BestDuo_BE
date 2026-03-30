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

  @Column(name = "last_evaluated_at", nullable = false)
  private OffsetDateTime lastEvaluatedAt;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

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

  public void refreshCount(long currentMatchCount) {
    this.currentMatchCount = currentMatchCount;
    this.status = currentMatchCount >= targetMatchCount
        ? CoverageBucketStatus.SUFFICIENT
        : CoverageBucketStatus.COLLECTING;
    this.lastEvaluatedAt = OffsetDateTime.now();
    this.updatedAt = this.lastEvaluatedAt;
  }
}
