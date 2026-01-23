package com.bestduo_BE.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "bottom_duo_matchup_agg")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@IdClass(BottomDuoMatchupAggId.class)
public class BottomDuoMatchupAgg {

  @Id
  @Column(name = "my_adc_champion_id", nullable = false)
  private String myAdcChampionId;

  @Id
  @Column(name = "my_sup_champion_id", nullable = false)
  private String mySupChampionId;

  @Id
  @Column(name = "opp_adc_champion_id", nullable = false)
  private String oppAdcChampionId;

  @Id
  @Column(name = "opp_sup_champion_id", nullable = false)
  private String oppSupChampionId;

  @Id
  @Column(name = "tier", nullable = false)
  private String tier;

  @Column(name = "wins", nullable = false)
  private int wins;

  @Column(name = "games", nullable = false)
  private int games;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;
}
