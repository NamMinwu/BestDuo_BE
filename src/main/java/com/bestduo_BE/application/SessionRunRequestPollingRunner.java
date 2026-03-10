package com.bestduo_BE.application;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SessionRunRequestPollingRunner {

  private final SessionRunRequestWorker worker;

  @Value("${session-run-request.worker.enabled:false}")
  private boolean enabled;

  @PostConstruct
  public void start() {
    if (!enabled) {
      log.info("SessionRunRequestPollingRunner disabled.");
      return;
    }

    Thread.startVirtualThread(() -> {
      log.info("SessionRunRequestPollingRunner started.");
      while (true) {
        try {
          worker.pollAndRunOnce();
          Thread.sleep(5000);
        } catch (Exception e) {
          log.error("SessionRunRequestPollingRunner loop failed.", e);
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
