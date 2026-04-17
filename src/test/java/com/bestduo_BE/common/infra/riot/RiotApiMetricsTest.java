package com.bestduo_BE.common.infra.riot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RiotApiMetricsTest {

  @Test
  @DisplayName("success supplier 호출 시 riot.api.request{outcome=success} timer가 1회 기록된다")
  void record_successSupplier_incrementsSuccessTimer() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    RiotApiMetrics metrics = new RiotApiMetrics(registry);

    String result = metrics.record("loadMatch", () -> "ok");

    assertThat(result).isEqualTo("ok");
    Timer timer = registry.timer("riot.api.request", "endpoint", "loadMatch", "outcome", "success");
    assertThat(timer.count()).isEqualTo(1L);
  }

  @Test
  @DisplayName("예외 발생 시 riot.api.request{outcome=error} timer가 기록되고 예외가 전파된다")
  void record_exceptionPropagated_incrementsErrorTimer() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    RiotApiMetrics metrics = new RiotApiMetrics(registry);

    assertThatThrownBy(() -> metrics.record("loadMatch", () -> {
      throw new RuntimeException("boom");
    })).isInstanceOf(RuntimeException.class);

    Timer timer = registry.timer("riot.api.request", "endpoint", "loadMatch", "outcome", "error");
    assertThat(timer.count()).isEqualTo(1L);
  }

  @Test
  @DisplayName("endpoint 태그 값이 다르면 독립된 timer로 기록된다")
  void record_differentEndpoints_produceDistinctTimers() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    RiotApiMetrics metrics = new RiotApiMetrics(registry);

    metrics.record("loadMatch", () -> "a");
    metrics.record("findRecentMatchIds", () -> "b");

    assertThat(registry.timer("riot.api.request", "endpoint", "loadMatch", "outcome", "success").count())
        .isEqualTo(1L);
    assertThat(registry.timer("riot.api.request", "endpoint", "findRecentMatchIds", "outcome", "success").count())
        .isEqualTo(1L);
  }
}
