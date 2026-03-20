package com.bestduo_BE.orchestration.infra.persistence.entity;

import com.bestduo_BE.common.domain.model.Tier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "session_run_request")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class SessionRunRequest {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String status; // REQUESTED, RUNNING, DONE, ERROR

  @Column(nullable = false)
  private int budgetTotal;

  @Column(nullable = false)
  private double seedRatio;

  @Column(nullable = false)
  private double refreshRatio;

  @Column(nullable = false)
  private int consumeLimitPerCycle;

  @Column(nullable = false)
  private int maxConsumeCycles;

  @Column(nullable = false)
  private int refreshLimit;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Tier tier;

  @Column(nullable = false)
  private OffsetDateTime requestedAt;

  private OffsetDateTime startedAt;

  private OffsetDateTime endedAt;

  private String message;

  public static SessionRunRequest newRequested(
      int budgetTotal,
      double seedRatio,
      double refreshRatio,
      int consumeLimitPerCycle,
      int maxConsumeCycles,
      int refreshLimit,
      Tier tier
  ) {
    return SessionRunRequest.builder()
        .status("REQUESTED")
        .budgetTotal(budgetTotal)
        .seedRatio(seedRatio)
        .refreshRatio(refreshRatio)
        .consumeLimitPerCycle(consumeLimitPerCycle)
        .maxConsumeCycles(maxConsumeCycles)
        .refreshLimit(refreshLimit)
        .tier(tier)
        .requestedAt(OffsetDateTime.now())
        .build();
  }

  public void markRunning() {
    this.status = "RUNNING";
    this.startedAt = OffsetDateTime.now();
  }

  public void markDone(String message) {
    this.status = "DONE";
    this.endedAt = OffsetDateTime.now();
    this.message = message;
  }

  public void markError(String message) {
    this.status = "ERROR";
    this.endedAt = OffsetDateTime.now();
    this.message = message;
  }
}
