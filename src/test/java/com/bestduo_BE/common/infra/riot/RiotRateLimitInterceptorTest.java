package com.bestduo_BE.common.infra.riot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bestduo_BE.common.infra.riot.exception.RiotRateLimitedException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

class RiotRateLimitInterceptorTest {

  private MutableClock clock;
  private RiotRateLimitInterceptor interceptor;
  private MockClientHttpRequest request;

  @BeforeEach
  void setUp() {
    clock = new MutableClock(Instant.parse("2026-03-30T00:00:00Z"));
    interceptor = new RiotRateLimitInterceptor(
        "test-key",
        new DualWindowRateLimiter(10, Duration.ofSeconds(1), 60, Duration.ofMinutes(2)),
        clock
    );
    request = new MockClientHttpRequest();
    request.setURI(URI.create("https://riot.api/test"));
  }

  @Test
  void headerSetAndResponseReturnedWhenRequestSucceeds() throws Exception {
    ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
    ClientHttpResponse ok = new MockClientHttpResponse(new byte[0], HttpStatus.OK);
    when(execution.execute(any(), any())).thenReturn(ok);

    ClientHttpResponse result = interceptor.intercept(request, new byte[0], execution);

    assertThat(result).isSameAs(ok);
    assertThat(request.getHeaders().getFirst("X-Riot-Token")).isEqualTo("test-key");
  }

  @Test
  void throwsRiotRateLimitedExceptionOnTooManyRequests() throws Exception {
    ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
    ClientHttpResponse tooMany = mock(ClientHttpResponse.class);
    var headers = new org.springframework.http.HttpHeaders();
    headers.set("Retry-After", "10");
    when(tooMany.getStatusCode()).thenReturn(HttpStatus.TOO_MANY_REQUESTS);
    when(tooMany.getHeaders()).thenReturn(headers);
    when(execution.execute(any(), any())).thenReturn(tooMany);

    assertThatThrownBy(() -> interceptor.intercept(request, new byte[0], execution))
        .isInstanceOf(RiotRateLimitedException.class)
        .hasMessageContaining("retry-after=10")
        .hasMessageContaining("uri=" + request.getURI());

    verify(tooMany).close();
  }

  @Test
  void throwsImmediatelyWhenStillCoolingDown() throws Exception {
    ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
    ClientHttpResponse tooMany = mock(ClientHttpResponse.class);
    var headers = new org.springframework.http.HttpHeaders();
    headers.set("Retry-After", "30");
    when(tooMany.getStatusCode()).thenReturn(HttpStatus.TOO_MANY_REQUESTS);
    when(tooMany.getHeaders()).thenReturn(headers);
    when(execution.execute(any(), any())).thenReturn(tooMany);

    // 첫 번째 요청으로 429 수신 → cooldown 30s 설정
    assertThatThrownBy(() -> interceptor.intercept(request, new byte[0], execution))
        .isInstanceOf(RiotRateLimitedException.class);

    // 10초 경과 (cooldown 아직 20s 남음)
    clock.advance(Duration.ofSeconds(10));

    // 두 번째 요청은 execute()도 호출 안 하고 즉시 throw
    ClientHttpRequestExecution execution2 = mock(ClientHttpRequestExecution.class);
    assertThatThrownBy(() -> interceptor.intercept(request, new byte[0], execution2))
        .isInstanceOf(RiotRateLimitedException.class)
        .hasMessageContaining("cooling down");
    verify(execution2, never()).execute(any(), any());
  }

  @Test
  void durationUntilAvailableIsZeroWhenNotCooling() {
    assertThat(interceptor.durationUntilAvailable()).isEqualTo(Duration.ZERO);
  }

  @Test
  void durationUntilAvailableReflectsCooldown() throws Exception {
    ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
    ClientHttpResponse tooMany = mock(ClientHttpResponse.class);
    var headers = new org.springframework.http.HttpHeaders();
    headers.set("Retry-After", "30");
    when(tooMany.getStatusCode()).thenReturn(HttpStatus.TOO_MANY_REQUESTS);
    when(tooMany.getHeaders()).thenReturn(headers);
    when(execution.execute(any(), any())).thenReturn(tooMany);

    assertThatThrownBy(() -> interceptor.intercept(request, new byte[0], execution))
        .isInstanceOf(RiotRateLimitedException.class);

    Duration wait = interceptor.durationUntilAvailable();
    assertThat(wait).isGreaterThan(Duration.ofSeconds(29));
    assertThat(wait).isLessThanOrEqualTo(Duration.ofSeconds(30));
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
