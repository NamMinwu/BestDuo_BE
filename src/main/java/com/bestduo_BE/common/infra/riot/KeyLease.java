package com.bestduo_BE.common.infra.riot;

import java.time.Duration;

public class KeyLease implements AutoCloseable {

  private final RiotKeyState keyState;

  public KeyLease(RiotKeyState keyState) {
    this.keyState = keyState;
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
  }
}
