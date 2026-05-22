package com.bestduo_BE.common.infra.riot;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class RiotApiMetrics {

  private static final String REQUEST_METRIC = "riot.api.request";
  private static final String RATE_LIMITED_METRIC = "riot.api.rate_limited";

  private final MeterRegistry registry;

  public RiotApiMetrics(MeterRegistry registry) {
    this.registry = registry;
  }

  public <T> T record(String endpoint, Supplier<T> call) {
    Timer.Sample sample = Timer.start(registry);
    try {
      T result = call.get();
      sample.stop(registry.timer(REQUEST_METRIC, "endpoint", endpoint, "outcome", "success"));
      return result;
    } catch (RuntimeException e) {
      sample.stop(registry.timer(REQUEST_METRIC, "endpoint", endpoint, "outcome", "error"));
      throw e;
    }
  }

  /**
   * 429 발생 카운터. Riot 의 {@code X-Rate-Limit-Type} 헤더로 원인 분류.
   *
   * @param type {@code application} (앱-level 한도) / {@code method} (endpoint 한도) /
   *     {@code service} (Riot 서비스 부하) / {@code unknown} (헤더 결손)
   * @param endpoint 정규화된 path (cardinality 폭발 방지)
   */
  public void recordRateLimited(String type, String endpoint) {
    registry
        .counter(
            RATE_LIMITED_METRIC,
            "type", type != null && !type.isBlank() ? type : "unknown",
            "endpoint", endpoint != null && !endpoint.isBlank() ? endpoint : "unknown")
        .increment();
  }
}
