package com.bestduo_BE.common.infra.riot;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

public class RiotKeyState {

  private final String apiKey;
  private final DualWindowRateLimiter rateLimiter;
  private final Clock clock;
  private Instant cooldownUntil;

  public RiotKeyState(String apiKey, DualWindowRateLimiter rateLimiter, Clock clock) {
    this.apiKey = apiKey;
    this.rateLimiter = rateLimiter;
    this.clock = clock;
    this.cooldownUntil = Instant.EPOCH;
  }

  public String apiKey() {
    return apiKey;
  }

  public void acquire() {
    rateLimiter.acquire();
  }

  public synchronized boolean isAvailable() {
    return !clock.instant().isBefore(cooldownUntil);
  }

  public synchronized void markRateLimited(Duration retryAfter) {
    Duration cooldown = retryAfter == null || retryAfter.isNegative() ? Duration.ofSeconds(10) : retryAfter;
    Instant candidateCooldownUntil = clock.instant().plus(cooldown);
    if (candidateCooldownUntil.isAfter(this.cooldownUntil)) {
      this.cooldownUntil = candidateCooldownUntil;
    }
  }
}
