package com.bestduo_BE.common.infra.persistence.entity;

import com.bestduo_BE.common.domain.model.Tier;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 날짜별 파이프라인 진행 상태를 영속화한다.
 *
 * <p>재시작 복구: pipeline_date = TODAY 행이 있으면 이어서 진행, 없으면 새 행 생성.
 */
@Entity
@Table(
    name = "daily_pipeline_state",
    uniqueConstraints = @UniqueConstraint(name = "uk_daily_pipeline_state_date", columnNames = "pipeline_date")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class DailyPipelineState {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "pipeline_date", nullable = false)
  private LocalDate pipelineDate;

  @Builder.Default
  @Column(name = "seed_api_calls_used", nullable = false)
  private int seedApiCallsUsed = 0;

  @Builder.Default
  @Column(name = "collect_api_calls_used", nullable = false)
  private int collectApiCallsUsed = 0;

  /** 오늘 seed 완료된 tier 목록. */
  @Builder.Default
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "daily_pipeline_seed_completed_tiers",
      joinColumns = @JoinColumn(name = "daily_pipeline_state_id")
  )
  @Enumerated(EnumType.STRING)
  @Column(name = "tier", nullable = false)
  private Set<Tier> seedCompletedTiers = EnumSet.noneOf(Tier.class);

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  public static DailyPipelineState create(LocalDate date) {
    OffsetDateTime now = OffsetDateTime.now();
    return DailyPipelineState.builder()
        .pipelineDate(date)
        .seedApiCallsUsed(0)
        .collectApiCallsUsed(0)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }

  public void incrementCollectCalls(int delta) {
    this.collectApiCallsUsed += delta;
    this.updatedAt = OffsetDateTime.now();
  }

  /** 오늘 해당 tier의 seed가 완료됐으면 true. */
  public boolean isSeedTierCompleted(Tier tier) {
    return seedCompletedTiers.contains(tier);
  }

  /** seed 완료된 tier를 기록한다. 중복은 무시된다. */
  public void recordSeedCompletedTier(Tier tier) {
    seedCompletedTiers.add(tier);
    this.updatedAt = OffsetDateTime.now();
  }
}
