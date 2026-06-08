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
  private static final String MATCH_QUEUE_SIZE = "pipeline.match_queue.size";
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
   * Stage 2 가 match_queue 에 enqueue 한 match 수 카운트. Stage 2 호출 수 (stage.completed{stage=2})
   * 와 비교해 호출당 평균 enqueue 수 추적 → Stage 2/3 분배 정책 결정의 입력.
   */
  public void recordMatchesEnqueued(int count) {
    if (count > 0) {
      registry.counter(COLLECT_MATCHES_ENQUEUED).increment(count);
    }
  }

  public void registerMatchQueueGauge(Supplier<Number> sizeSupplier) {
    // strongReference(true): Micrometer 는 기본적으로 supplier 를 weak reference 로 보유한다.
    // MatchQueueGaugeRegistrar 에서 전달하는 인라인 람다가 GC 되면 gauge 값이 NaN 으로 떨어지는 문제를 방지한다.
    Gauge.builder(MATCH_QUEUE_SIZE, sizeSupplier, s -> s.get().doubleValue())
        .strongReference(true)
        .register(registry);
  }

  /**
   * Stage 3 한 배치의 ingest 결과(성공/실패 매치 수)를 기록한다.
   * 큐 제거(ADR-008) 후 inline 경로에서도 동일 메트릭을 재사용해 parity 를 유지한다.
   */
  public void recordIngestOutcome(int success, int failure) {
    if (success > 0) {
      registry.counter(INGEST_SUCCESS).increment(success);
    }
    if (failure > 0) {
      registry.counter(INGEST_FAILURE).increment(failure);
    }
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
