package com.bestduo_BE.common.infra.riot;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

public class RiotKeyState {

  private final String apiKey;
  private final DualWindowRateLimiter rateLimiter;
  private final Clock clock;
  private Instant cooldownUntil;
  private boolean leased;

  public RiotKeyState(String apiKey, DualWindowRateLimiter rateLimiter, Clock clock) {
    this.apiKey = apiKey;
    this.rateLimiter = rateLimiter;
    this.clock = clock;
    this.cooldownUntil = Instant.EPOCH;
    this.leased = false;
  }

  public String apiKey() {
    return apiKey;
  }

  public void acquire() {
    rateLimiter.acquire();
  }

  public synchronized boolean isAvailable() {
    return !leased && !clock.instant().isBefore(cooldownUntil);
  }

  public synchronized boolean tryLease() {
    if (!isAvailable()) {
      return false;
    }
    this.leased = true;
    return true;
  }

  public synchronized void releaseLease() {
    this.leased = false;
  }

  public synchronized Instant cooldownUntil() {
    return cooldownUntil;
  }

  public synchronized void markRateLimited(Duration retryAfter) {
    Duration cooldown = retryAfter == null || retryAfter.isNegative() ? Duration.ofSeconds(10) : retryAfter;
    Instant candidateCooldownUntil = clock.instant().plus(cooldown);
    if (candidateCooldownUntil.isAfter(this.cooldownUntil)) {
      this.cooldownUntil = candidateCooldownUntil;
    }
  }
}
