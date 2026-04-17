package com.bestduo_BE.pipeline.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PipelineMetricsTest {

  @Test
  @DisplayName("stage 성공 시 pipeline.stage.completed{outcome=success} counter가 증가한다")
  void recordSuccess_incrementsSuccessCounter() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    PipelineMetrics metrics = new PipelineMetrics(registry);

    metrics.recordStageCompleted(1, "success");
    metrics.recordStageCompleted(1, "success");

    double count = registry.counter("pipeline.stage.completed", "stage", "1", "outcome", "success").count();
    assertThat(count).isEqualTo(2.0);
  }

  @Test
  @DisplayName("stage 실패 시 pipeline.stage.completed{outcome=error} counter가 증가한다")
  void recordError_incrementsErrorCounter() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    PipelineMetrics metrics = new PipelineMetrics(registry);

    metrics.recordStageCompleted(3, "error");

    double count = registry.counter("pipeline.stage.completed", "stage", "3", "outcome", "error").count();
    assertThat(count).isEqualTo(1.0);
  }

  @Test
  @DisplayName("stage 태그 값이 달라도 각각 독립된 counter가 된다")
  void differentStages_produceDistinctCounters() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    PipelineMetrics metrics = new PipelineMetrics(registry);

    metrics.recordStageCompleted(1, "success");
    metrics.recordStageCompleted(2, "success");

    assertThat(registry.counter("pipeline.stage.completed", "stage", "1", "outcome", "success").count())
        .isEqualTo(1.0);
    assertThat(registry.counter("pipeline.stage.completed", "stage", "2", "outcome", "success").count())
        .isEqualTo(1.0);
  }

  @Test
  @DisplayName("registerMatchQueueGauge는 pipeline.match_queue.size 게이지를 현재 supplier 값으로 노출한다")
  void registerMatchQueueGauge_reflectsCurrentSupplierValue() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    PipelineMetrics metrics = new PipelineMetrics(registry);
    AtomicLong size = new AtomicLong(42L);

    metrics.registerMatchQueueGauge(size::get);

    Gauge gauge = registry.find("pipeline.match_queue.size").gauge();
    assertThat(gauge).isNotNull();
    assertThat(gauge.value()).isEqualTo(42.0);

    size.set(7L);
    assertThat(gauge.value()).isEqualTo(7.0);
  }
}
