package com.bestduo_BE.orchestration.application;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExecutionRequestPoller {

  private final ExecutionRequestWorker executionRequestWorker;

  @Value("${run-request.worker.enabled:false}")
  private boolean enabled;

  @PostConstruct
  public void start() {
    if (!enabled) {
      log.info("ExecutionRequestPoller disabled.");
      return;
    }

    Thread.startVirtualThread(() -> {
      log.info("ExecutionRequestPoller started.");
      while (true) {
        try {
          executionRequestWorker.pollAndRunOnce();
          Thread.sleep(5000);
        } catch (Exception e) {
          log.error("ExecutionRequestPoller loop failed.", e);
          try {
            Thread.sleep(5000);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return;
          }
        }
      }
    });
  }
}
