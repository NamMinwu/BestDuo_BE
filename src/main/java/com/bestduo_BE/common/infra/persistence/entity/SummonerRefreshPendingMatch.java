package com.bestduo_BE.common.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "summoner_refresh_pending_match")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class SummonerRefreshPendingMatch {

  @EmbeddedId
  private SummonerRefreshPendingMatchId id;

  @Column(name = "response_index", nullable = false)
  private int responseIndex;

  @Column(name = "match_start_time_sec")
  private Long matchStartTimeSec;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  public static SummonerRefreshPendingMatch newPending(
      String puuid,
      String matchId,
      int responseIndex,
      Long matchStartTimeSec
  ) {
    OffsetDateTime now = OffsetDateTime.now();
    return SummonerRefreshPendingMatch.builder()
        .id(new SummonerRefreshPendingMatchId(puuid, matchId))
        .responseIndex(responseIndex)
        .matchStartTimeSec(matchStartTimeSec)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }

  public void confirm(Long matchStartTimeSec) {
    if (matchStartTimeSec == null) {
      return;
    }
    if (this.matchStartTimeSec == null || this.matchStartTimeSec < matchStartTimeSec) {
      this.matchStartTimeSec = matchStartTimeSec;
    }
    this.updatedAt = OffsetDateTime.now();
  }
}
