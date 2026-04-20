package com.bestduo_BE.pipeline.application;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class PipelineMetrics {

  private static final String STAGE_COMPLETED = "pipeline.stage.completed";
  private static final String MATCH_QUEUE_SIZE = "pipeline.match_queue.size";

  private final MeterRegistry registry;

  public PipelineMetrics(MeterRegistry registry) {
    this.registry = registry;
  }

  public void recordStageCompleted(int stage, String outcome) {
    registry.counter(STAGE_COMPLETED, "stage", String.valueOf(stage), "outcome", outcome)
        .increment();
  }

  public void registerMatchQueueGauge(Supplier<Number> sizeSupplier) {
    // strongReference(true): Micrometer 는 기본적으로 supplier 를 weak reference 로 보유한다.
    // MatchQueueGaugeRegistrar 에서 전달하는 인라인 람다가 GC 되면 gauge 값이 NaN 으로 떨어지는 문제를 방지한다.
    Gauge.builder(MATCH_QUEUE_SIZE, sizeSupplier, s -> s.get().doubleValue())
        .strongReference(true)
        .register(registry);
  }
}
