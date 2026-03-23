package com.bestduo_BE.common.infra.riot;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

public class DualWindowRateLimiter {

  private final int shortLimit;
  private final Duration shortWindow;

  private final int longLimit;
  private final Duration longWindow;

  private final Deque<Instant> shortHits = new ArrayDeque<>();
  private final Deque<Instant> longHits = new ArrayDeque<>();

  public DualWindowRateLimiter(
      int shortLimit, Duration shortWindow,
      int longLimit, Duration longWindow
  ) {
    this.shortLimit = shortLimit;
    this.shortWindow = shortWindow;
    this.longLimit = longLimit;
    this.longWindow = longWindow;
  }

  /** 1회 호출 = 1 request */
  public void acquire() {
    while (true) {
      long sleepMs = 0L;
      synchronized (this) {
        Instant now = Instant.now();
        purgeOld(now);

        long shortWait = waitMsIfFull(now, shortHits, shortLimit, shortWindow);
        long longWait = waitMsIfFull(now, longHits, longLimit, longWindow);

        sleepMs = Math.max(shortWait, longWait);

        if (sleepMs <= 0) {
          // consume token
          shortHits.addLast(now);
          longHits.addLast(now);
          return;
        }
      }

      // sleep outside synchronized
      try {
        Thread.sleep(sleepMs);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException("Interrupted while waiting for Riot rate limit", e);
      }
    }
  }

  private void purgeOld(Instant now) {
    purgeDeque(now, shortHits, shortWindow);
    purgeDeque(now, longHits, longWindow);
  }

  private void purgeDeque(Instant now, Deque<Instant> deque, Duration window) {
    Instant threshold = now.minus(window);
    while (!deque.isEmpty() && deque.peekFirst().isBefore(threshold)) {
      deque.removeFirst();
    }
  }

  private long waitMsIfFull(Instant now, Deque<Instant> deque, int limit, Duration window) {
    if (deque.size() < limit) return 0L;
    Instant oldest = deque.peekFirst();
    Instant allowedAt = oldest.plus(window);
    long ms = Duration.between(now, allowedAt).toMillis();
    return Math.max(ms, 0L);
  }
}
