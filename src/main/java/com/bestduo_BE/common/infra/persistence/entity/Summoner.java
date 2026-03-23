package com.bestduo_BE.common.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "summoner")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Summoner {

  @Id
  @Column(name = "puuid", nullable = false)
  private String puuid;

  @Column(name = "last_match_start_time")
  private Long lastMatchStartTime; // epoch seconds

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  public static Summoner create(String puuid) {
    OffsetDateTime now = OffsetDateTime.now();
    return Summoner.builder()
        .puuid(puuid)
        .lastMatchStartTime(null)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }

  public void advanceLastMatchStartTime(Long newLastMatchStartTimeOrNull) {
    if (newLastMatchStartTimeOrNull != null
        && (this.lastMatchStartTime == null || this.lastMatchStartTime < newLastMatchStartTimeOrNull)) {
      this.lastMatchStartTime = newLastMatchStartTimeOrNull;
    }
    this.updatedAt = OffsetDateTime.now();
  }
}
