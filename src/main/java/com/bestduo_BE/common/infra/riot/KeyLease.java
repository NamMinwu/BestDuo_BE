package com.bestduo_BE.common.infra.riot;

import java.time.Duration;

public class KeyLease implements AutoCloseable {

  private final RiotKeyState keyState;
  private final boolean releasable;

  public KeyLease(RiotKeyState keyState) {
    this(keyState, true);
  }

  public KeyLease(RiotKeyState keyState, boolean releasable) {
    this.keyState = keyState;
    this.releasable = releasable;
  }

  public String apiKey() {
    return keyState.apiKey();
  }

  public void acquire() {
    keyState.acquire();
  }

  public void markRateLimited(Duration retryAfter) {
    keyState.markRateLimited(retryAfter);
  }

  @Override
  public void close() {
    if (releasable) {
      keyState.releaseLease();
    }
  }
}
