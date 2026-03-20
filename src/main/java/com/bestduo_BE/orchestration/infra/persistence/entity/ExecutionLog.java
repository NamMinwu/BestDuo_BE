package com.bestduo_BE.orchestration.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "session_run_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ExecutionLog {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "started_at", nullable = false)
  private OffsetDateTime startedAt;

  @Column(name = "ended_at", nullable = false)
  private OffsetDateTime endedAt;

  @Column(name = "stop_reason", nullable = false)
  private String stopReason; // DONE / STOPPED_BUDGET / STOPPED_429 / ERROR

  @Column(name = "budget_total", nullable = false)
  private int budgetTotal;

  @Column(name = "seed_budget", nullable = false)
  private int seedBudget;

  @Column(name = "refresh_budget", nullable = false)
  private int refreshBudget;

  @Column(name = "consume_budget", nullable = false)
  private int ingestBudget;

  @Column(name = "seed_enqueued", nullable = false)
  private int seedEnqueued;

  @Column(name = "refresh_enqueued", nullable = false)
  private int refreshEnqueued;

  @Column(name = "picked", nullable = false)
  private int picked;

  @Column(name = "done", nullable = false)
  private int done;

  @Column(name = "error", nullable = false)
  private int error;

  @Column(name = "raw_created", nullable = false)
  private int rawCreated;

  @Column(name = "message")
  private String message;

  public static ExecutionLog of(ExecutionResult r) {
    return ExecutionLog.builder()
        .startedAt(r.startedAt())
        .endedAt(r.endedAt())
        .stopReason(r.stopReason())
        .budgetTotal(r.budgetTotal())
        .seedBudget(r.seedBudget())
        .refreshBudget(r.refreshBudget())
        .ingestBudget(r.ingestBudget())
        .seedEnqueued(r.seedEnqueued())
        .refreshEnqueued(r.refreshEnqueued())
        .picked(r.picked())
        .done(r.done())
        .error(r.error())
        .rawCreated(r.rawCreated())
        .message(r.message())
        .build();
  }

  public record ExecutionResult(
      OffsetDateTime startedAt,
      OffsetDateTime endedAt,
      String stopReason,
      int budgetTotal,
      int seedBudget,
      int refreshBudget,
      int ingestBudget,
      int seedEnqueued,
      int refreshEnqueued,
      int picked,
      int done,
      int error,
      int rawCreated,
      String message
  ) {}
}
