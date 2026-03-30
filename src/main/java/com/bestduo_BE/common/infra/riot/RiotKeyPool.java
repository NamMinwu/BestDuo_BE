package com.bestduo_BE.common.infra.riot;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class RiotKeyPool {

  private static final Duration DEFAULT_RETRY_AFTER = Duration.ofSeconds(10);

  private final List<RiotKeyState> keyStates;
  private final AtomicInteger nextIndex = new AtomicInteger();
  private final ThreadLocal<RiotKeyState> workerLease = new ThreadLocal<>();

  public RiotKeyPool(List<RiotKeyState> keyStates) {
    this.keyStates = List.copyOf(keyStates);
  }

  public static RiotKeyPool fromKeys(List<String> apiKeys, Clock clock) {
    return new RiotKeyPool(apiKeys.stream()
        .map(key -> new RiotKeyState(
            key,
            new DualWindowRateLimiter(10, Duration.ofSeconds(1), 60, Duration.ofMinutes(2)),
            clock
        ))
        .toList());
  }

  public KeyLease leaseForWorker() {
    KeyLease lease = selectAvailableLease();
    workerLease.set(keyStates.stream()
        .filter(state -> state.apiKey().equals(lease.apiKey()))
        .findFirst()
        .orElseThrow());
    return lease;
  }

  public KeyLease leaseForRequest() {
    RiotKeyState reserved = workerLease.get();
    if (reserved != null) {
      return new KeyLease(reserved, false);
    }
    return selectAvailableLease();
  }

  public KeyLease lease() {
    return leaseForRequest();
  }

  private KeyLease selectAvailableLease() {
    if (keyStates.isEmpty()) {
      throw new IllegalStateException("No Riot API keys configured");
    }

    int start = Math.floorMod(nextIndex.getAndIncrement(), keyStates.size());
    for (int offset = 0; offset < keyStates.size(); offset++) {
      RiotKeyState state = keyStates.get((start + offset) % keyStates.size());
      if (state.tryLease()) {
        return new KeyLease(state);
      }
    }

    throw new IllegalStateException("All Riot API keys are cooling down");
  }

  public Duration defaultRetryAfter() {
    return DEFAULT_RETRY_AFTER;
  }

  public void clearWorkerLease() {
    workerLease.remove();
  }
}
