package com.bestduo_BE.pipeline.application;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class PipelineMetrics {

  private static final String STAGE_COMPLETED = "pipeline.stage.completed";
  private static final String COLLECT_MATCHES_ENQUEUED = "pipeline.collect.matches_enqueued";
  private static final String INGEST_SUCCESS = "pipeline.ingest.success";
  private static final String INGEST_FAILURE = "pipeline.ingest.failure";
  private static final String PENDING_SUMMONERS = "pipeline.collect.pending_summoners";
  private static final String HEARTBEAT = "pipeline.heartbeat";

  private final MeterRegistry registry;
  private final AtomicLong lastHeartbeatEpochSeconds = new AtomicLong(0L);

  public PipelineMetrics(MeterRegistry registry) {
    this.registry = registry;
    Gauge.builder(HEARTBEAT, lastHeartbeatEpochSeconds, AtomicLong::doubleValue)
        .strongReference(true)
        .register(registry);
  }

  public void recordStageCompleted(int stage, String outcome) {
    registry.counter(STAGE_COMPLETED, "stage", String.valueOf(stage), "outcome", outcome)
        .increment();
  }

  /**
   * Stage 2 가 한 summoner 에서 찾은 matchId 수 카운트(inline 수집).
   * 메트릭 이름은 레거시({@code pipeline.collect.matches_enqueued})를 유지해 대시보드 연속성을 지킨다.
   */
  public void recordMatchesEnqueued(int count) {
    if (count > 0) {
      registry.counter(COLLECT_MATCHES_ENQUEUED).increment(count);
    }
  }

  /** 매치 1건 ingest 성공. (Phase A 의 batch counter 와 동일 {@code pipeline.ingest.success} 시리즈 — 연속성 유지.) */
  public void recordIngestSuccess() {
    registry.counter(INGEST_SUCCESS).increment();
  }

  /**
   * 매치 1건 ingest 실패. reason 은 저-cardinality 분류(auth/server_5xx/timeout/not_found/client_4xx/other).
   * NFR-4: reason=auth/server_5xx 급증을 시스템 실패 알람으로 사용.
   */
  public void recordIngestFailure(String reason) {
    registry.counter(INGEST_FAILURE, "reason", reason).increment();
  }

  public void registerPendingSummonersGauge(Supplier<Number> countSupplier) {
    Gauge.builder(PENDING_SUMMONERS, countSupplier, s -> s.get().doubleValue())
        .strongReference(true)
        .register(registry);
  }

  /** 파이프라인 루프가 살아있음을 표시한다. 알람: {@code time() - pipeline_heartbeat > 임계}. */
  public void recordHeartbeat() {
    lastHeartbeatEpochSeconds.set(Instant.now().getEpochSecond());
  }
}
