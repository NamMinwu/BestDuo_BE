package com.bestduo_BE.common.infra.riot;

import com.bestduo_BE.common.infra.riot.exception.RiotRateLimitedException;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

public class RiotRateLimitInterceptor implements ClientHttpRequestInterceptor {

  private static final Duration DEFAULT_RETRY_AFTER = Duration.ofSeconds(10);

  private final String apiKey;
  private final DualWindowRateLimiter rateLimiter;
  private final Clock clock;
  private volatile Instant rateLimitedUntil = Instant.EPOCH;

  public RiotRateLimitInterceptor(String apiKey, DualWindowRateLimiter rateLimiter, Clock clock) {
    this.apiKey = apiKey;
    this.rateLimiter = rateLimiter;
    this.clock = clock;
  }

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution
  ) throws IOException {

    Duration wait = durationUntilAvailable();
    if (!wait.isZero()) {
      throw new RiotRateLimitedException(
          "Riot API cooling down. retry-in=" + wait.toSeconds() + "s, uri=" + request.getURI());
    }

    rateLimiter.acquire();
    request.getHeaders().set("X-Riot-Token", apiKey);

    ClientHttpResponse response = execution.execute(request, body);

    if (response.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
      String retryAfter = response.getHeaders().getFirst("Retry-After");
      markRateLimited(parseRetryAfter(retryAfter));
      String msg = "Riot API rate limited (429). retry-after=" + retryAfter
          + ", uri=" + request.getURI();
      response.close();
      throw new RiotRateLimitedException(msg);
    }

    return response;
  }

  public Duration durationUntilAvailable() {
    Instant now = clock.instant();
    if (!now.isBefore(rateLimitedUntil)) {
      return Duration.ZERO;
    }
    Duration remaining = Duration.between(now, rateLimitedUntil);
    return remaining.isNegative() ? Duration.ZERO : remaining;
  }

  private void markRateLimited(Duration retryAfter) {
    Instant candidate = clock.instant().plus(retryAfter);
    if (candidate.isAfter(rateLimitedUntil)) {
      rateLimitedUntil = candidate;
    }
  }

  private Duration parseRetryAfter(String retryAfter) {
    try {
      return retryAfter == null || retryAfter.isBlank()
          ? DEFAULT_RETRY_AFTER
          : Duration.ofSeconds(Long.parseLong(retryAfter));
    } catch (NumberFormatException e) {
      return DEFAULT_RETRY_AFTER;
    }
  }
}
