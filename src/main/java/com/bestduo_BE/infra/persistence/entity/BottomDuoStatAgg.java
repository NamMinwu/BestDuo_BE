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
@Table(name = "bottom_duo_stat_agg")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@IdClass(BottomDuoStatAggId.class)
public class BottomDuoStatAgg {

  @Id
  @Column(name = "adc_champion_id", nullable = false)
  private String adcChampionId;

  @Id
  @Column(name = "sup_champion_id", nullable = false)
  private String supChampionId;

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

