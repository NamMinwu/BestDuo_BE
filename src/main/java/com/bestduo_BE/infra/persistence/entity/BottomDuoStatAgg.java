package com.bestduo_BE.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "bottom_duo_stat_agg")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@IdClass(BottomDuoStatAggId.class)
public class BottomDuoStatAgg {

  @Id
  @Column(name = "patch_version", nullable = false, length = 20)
  private String patchVersion;

  @Id
  @Column(name = "adc_champion_id", nullable = false, length = 50)
  private String adcChampionId;

  @Id
  @Column(name = "sup_champion_id", nullable = false, length = 50)
  private String supChampionId;

  @Id
  @Column(name = "tier", nullable = false, length = 30)
  private String tier;

  @Column(name = "wins", nullable = false)
  private int wins;

  @Column(name = "games", nullable = false)
  private int games;

  @Column(name = "win_rate", nullable = false)
  private double winRate;

  @Column(name = "pick_rate", nullable = false)
  private double pickRate;

  @Column(name = "adjusted_win_rate", nullable = false)
  private double adjustedWinRate;

  @Column(name = "rank_score", nullable = false)
  private double rankScore;

  @Column(name = "ranking")
  private Integer ranking;

  @Column(name = "duo_tier")
  private Integer duoTier;

  @Column(name = "previous_ranking")
  private Integer previousRanking;

  @Column(name = "rank_delta")
  private Integer rankDelta;

  @Enumerated(EnumType.STRING)
  @Column(name = "ranking_status", nullable = false, length = 20)
  private RankingStatus rankingStatus;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  public String duoKey() {
    return adcChampionId + ":" + supChampionId + ":" + tier;
  }

  public void applyRankingMetrics(
      double pickRate,
      double rankScore,
      Integer ranking,
      Integer duoTier,
      Integer previousRanking,
      Integer rankDelta,
      OffsetDateTime newUpdatedAt
  ) {
    this.pickRate = pickRate;
    this.rankScore = rankScore;
    this.ranking = ranking;
    this.duoTier = duoTier;
    this.previousRanking = previousRanking;
    this.rankDelta = rankDelta;
    this.rankingStatus = RankingStatus.RANKED;
    this.updatedAt = Objects.requireNonNullElseGet(newUpdatedAt, OffsetDateTime::now);
  }

  public void markInsufficientData(
      double pickRate,
      int insufficientTier,
      OffsetDateTime newUpdatedAt
  ) {
    this.pickRate = pickRate;
    this.rankScore = 0;
    this.ranking = null;
    this.duoTier = insufficientTier;
    this.previousRanking = null;
    this.rankDelta = null;
    this.rankingStatus = RankingStatus.INSUFFICIENT;
    this.updatedAt = Objects.requireNonNullElseGet(newUpdatedAt, OffsetDateTime::now);
  }

  public enum RankingStatus {
    PENDING,
    RANKED,
    INSUFFICIENT
  }
}
