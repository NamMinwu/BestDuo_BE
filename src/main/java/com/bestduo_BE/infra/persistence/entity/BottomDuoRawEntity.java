package com.bestduo_BE.infra.persistence.entity;

import com.bestduo_BE.domain.model.BottomDuoRaw;
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
@Table(name = "bottom_duo_raw")
@IdClass(BottomDuoRawId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class BottomDuoRawEntity {

  @Id
  @Column(name = "match_id", nullable = false)
  private String matchId;

  @Id
  @Column(name = "team_id", nullable = false)
  private Integer teamId;

  @Column(name = "adc_champion_id", nullable = false)
  private Integer adcChampionId;

  @Column(name = "sup_champion_id", nullable = false)
  private Integer supChampionId;

  @Column(name = "win", nullable = false)
  private Boolean win;

  @Column(name = "patch")
  private String patch;

  @Column(name = "collection_tier", nullable = false)
  private String collectionTier;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  public static BottomDuoRawEntity fromDomain(BottomDuoRaw raw) {
    return BottomDuoRawEntity.builder()
        .matchId(raw.matchId())
        .teamId(raw.teamId())
        .adcChampionId(raw.adcChampionId())
        .supChampionId(raw.supChampionId())
        .win(raw.win())
        .patch(raw.patch())
        .collectionTier(raw.tier().name())
        .createdAt(OffsetDateTime.now())
        .build();
  }
}
