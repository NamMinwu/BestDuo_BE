package com.bestduo_BE.common.infra.riot;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class RiotApiMetrics {

  private static final String METRIC_NAME = "riot.api.request";

  private final MeterRegistry registry;

  public RiotApiMetrics(MeterRegistry registry) {
    this.registry = registry;
  }

  public <T> T record(String endpoint, Supplier<T> call) {
    Timer.Sample sample = Timer.start(registry);
    try {
      T result = call.get();
      sample.stop(registry.timer(METRIC_NAME, "endpoint", endpoint, "outcome", "success"));
      return result;
    } catch (RuntimeException e) {
      sample.stop(registry.timer(METRIC_NAME, "endpoint", endpoint, "outcome", "error"));
      throw e;
    }
  }
}
