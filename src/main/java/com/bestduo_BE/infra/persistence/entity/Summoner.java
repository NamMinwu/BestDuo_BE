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

  // Phase 2A: 리그 엔트리 기반 seed bootstrap 상태
  @Column(name = "seed_status", nullable = false)
  private String seedStatus; // READY/RUNNING/DONE/ERROR

  @Column(name = "last_seed_run_at")
  private OffsetDateTime lastSeedRunAt;

  // Phase 2B: match 참가자 기반 확장 상태
  @Column(name = "expand_status", nullable = false)
  private String expandStatus; // READY/RUNNING/DONE/ERROR

  @Column(name = "last_expand_run_at")
  private OffsetDateTime lastExpandRunAt;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  public static Summoner newReady(String puuid) {
    OffsetDateTime now = OffsetDateTime.now();
    return Summoner.builder()
        .puuid(puuid)
        .seedStatus("READY")
        .expandStatus("READY") // 처음 들어오면 확장 대상이기도 함
        .lastSeedRunAt(null)
        .lastExpandRunAt(null)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }

  // Phase 2A (Seed Bootstrap)
  public void markSeedRunning() {
    this.seedStatus = "RUNNING";
    this.lastSeedRunAt = OffsetDateTime.now();
    this.updatedAt = OffsetDateTime.now();
  }

  public void markSeedDone() {
    this.seedStatus = "DONE";
    this.updatedAt = OffsetDateTime.now();
  }

  public void markSeedError() {
    this.seedStatus = "ERROR";
    this.updatedAt = OffsetDateTime.now();
  }

  // Phase 2B (Seed Expansion)
  public void markExpandRunning() {
    this.expandStatus = "RUNNING";
    this.lastExpandRunAt = OffsetDateTime.now();
    this.updatedAt = OffsetDateTime.now();
  }

  public void markExpandDone() {
    this.expandStatus = "DONE";
    this.updatedAt = OffsetDateTime.now();
  }

  public void markExpandError() {
    this.expandStatus = "ERROR";
    this.updatedAt = OffsetDateTime.now();
  }
}
