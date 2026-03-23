package com.bestduo_BE.common.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class SummonerRefreshPendingMatchId implements Serializable {

  @Column(name = "puuid", nullable = false)
  private String puuid;

  @Column(name = "match_id", nullable = false)
  private String matchId;
}
