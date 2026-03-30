package com.bestduo_BE.common.infra.riot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class RiotKeyPoolTest {

  @Test
  void leaseRotatesAcrossAvailableKeys() {
    Clock clock = Clock.fixed(Instant.parse("2026-03-30T00:00:00Z"), ZoneOffset.UTC);
    RiotKeyPool keyPool = RiotKeyPool.fromKeys(List.of("key-a", "key-b"), clock);

    try (KeyLease first = keyPool.lease(); KeyLease second = keyPool.lease()) {
      assertThat(first.apiKey()).isEqualTo("key-a");
      assertThat(second.apiKey()).isEqualTo("key-b");
    }
  }

  @Test
  void leaseSkipsRateLimitedKey() {
    MutableClock clock = new MutableClock(Instant.parse("2026-03-30T00:00:00Z"));
    RiotKeyState first = new RiotKeyState("key-a", new DualWindowRateLimiter(10, Duration.ofMillis(1), 60, Duration.ofMinutes(2)), clock);
    RiotKeyState second = new RiotKeyState("key-b", new DualWindowRateLimiter(10, Duration.ofMillis(1), 60, Duration.ofMinutes(2)), clock);
    RiotKeyPool keyPool = new RiotKeyPool(List.of(first, second));

    first.markRateLimited(Duration.ofSeconds(30));

    try (KeyLease lease = keyPool.lease()) {
      assertThat(lease.apiKey()).isEqualTo("key-b");
    }
  }

  @Test
  void leaseFailsWhenAllKeysCoolingDown() {
    MutableClock clock = new MutableClock(Instant.parse("2026-03-30T00:00:00Z"));
    RiotKeyState first = new RiotKeyState("key-a", new DualWindowRateLimiter(10, Duration.ofMillis(1), 60, Duration.ofMinutes(2)), clock);
    RiotKeyPool keyPool = new RiotKeyPool(List.of(first));
    first.markRateLimited(Duration.ofSeconds(30));

    assertThatThrownBy(keyPool::lease)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("All Riot API keys are cooling down");
  }

  @Test
  void longerCooldownIsNotShortenedByLaterShorterRetryAfter() {
    MutableClock clock = new MutableClock(Instant.parse("2026-03-30T00:00:00Z"));
    RiotKeyState state = new RiotKeyState("key-a", new DualWindowRateLimiter(10, Duration.ofMillis(1), 60, Duration.ofMinutes(2)), clock);

    state.markRateLimited(Duration.ofSeconds(30));
    clock.advance(Duration.ofSeconds(5));
    state.markRateLimited(Duration.ofSeconds(1));
    clock.advance(Duration.ofSeconds(20));

    assertThat(state.isAvailable()).isFalse();
    clock.advance(Duration.ofSeconds(5));
    assertThat(state.isAvailable()).isTrue();
  }

  @Test
  void leaseForWorkerReleasesKeyOnClose() {
    RiotKeyPool keyPool = RiotKeyPool.fromKeys(List.of("key-a"), Clock.systemUTC());

    try (KeyLease ignored = keyPool.leaseForWorker()) {
      assertThatThrownBy(keyPool::leaseForWorker)
          .isInstanceOf(IllegalStateException.class);
    }

    try (KeyLease ignored = keyPool.leaseForWorker()) {
      assertThat(ignored.apiKey()).isEqualTo("key-a");
    }
  }

  private static final class MutableClock extends Clock {
    private Instant instant;

    private MutableClock(Instant instant) {
      this.instant = instant;
    }

    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }

    private void advance(Duration duration) {
      this.instant = this.instant.plus(duration);
    }
  }
}
