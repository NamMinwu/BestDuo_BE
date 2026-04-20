package com.bestduo_BE.pipeline.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.ref.WeakReference;
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

  @Test
  @DisplayName("registerMatchQueueGauge는 supplier 참조가 외부에 없어도 GC 후 NaN이 아닌 값을 반환한다")
  void registerMatchQueueGauge_survivesGcWhenSupplierHasNoExternalReference() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    PipelineMetrics metrics = new PipelineMetrics(registry);

    // 인라인 람다를 전달한 뒤 호출자가 참조를 유지하지 않는 상황을 재현한다.
    // Micrometer 는 기본적으로 supplier 를 weak reference 로 보유하므로,
    // strongReference(true) 가 없으면 GC 후 gauge 값이 NaN 으로 떨어진다.
    registerInlineLambdaGauge(metrics);

    forceGcUntilWeakReferenceCollected();

    Gauge gauge = registry.find("pipeline.match_queue.size").gauge();
    assertThat(gauge).isNotNull();
    assertThat(gauge.value()).isNotNaN();
    assertThat(gauge.value()).isEqualTo(123.0);
  }

  private void registerInlineLambdaGauge(PipelineMetrics metrics) {
    // 지역 변수를 캡처해 람다가 singleton 이 되지 않도록 한다.
    // MatchQueueGaugeRegistrar 에서 statsRepository 를 캡처하는 실제 패턴을 재현한다.
    AtomicLong localSource = new AtomicLong(123L);
    metrics.registerMatchQueueGauge(localSource::get);
  }

  private void forceGcUntilWeakReferenceCollected() {
    Object canary = new Object();
    WeakReference<Object> weakRef = new WeakReference<>(canary);
    canary = null;
    int attempts = 0;
    while (weakRef.get() != null && attempts < 50) {
      System.gc();
      try {
        Thread.sleep(10);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
      attempts++;
    }
  }
}
