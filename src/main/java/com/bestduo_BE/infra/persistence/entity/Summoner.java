package com.bestduo_BE.infra.persistence.entity;

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

  @Column(name = "seed_status", nullable = false)
  private String seedStatus; // READY/RUNNING/DONE/ERROR

  @Column(name = "last_seed_run_at")
  private OffsetDateTime lastSeedRunAt;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  public static Summoner newReady(String puuid) {
    OffsetDateTime now = OffsetDateTime.now();
    return Summoner.builder()
        .puuid(puuid)
        .seedStatus("READY")
        .lastSeedRunAt(null)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }

  public void markRunning() {
    this.seedStatus = "RUNNING";
    this.lastSeedRunAt = OffsetDateTime.now();
    this.updatedAt = OffsetDateTime.now();
  }

  public void markDone() {
    this.seedStatus = "DONE";
    this.updatedAt = OffsetDateTime.now();
  }

  public void markError() {
    this.seedStatus = "ERROR";
    this.updatedAt = OffsetDateTime.now();
  }
}
