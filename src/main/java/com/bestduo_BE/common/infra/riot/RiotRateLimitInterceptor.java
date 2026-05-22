package com.bestduo_BE.common.infra.riot;

import com.bestduo_BE.common.infra.riot.exception.RiotRateLimitedException;
import java.io.IOException;
import java.net.URI;
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
  private final RiotApiMetrics metrics;
  private volatile Instant rateLimitedUntil = Instant.EPOCH;

  public RiotRateLimitInterceptor(
      String apiKey, DualWindowRateLimiter rateLimiter, Clock clock, RiotApiMetrics metrics) {
    this.apiKey = apiKey;
    this.rateLimiter = rateLimiter;
    this.clock = clock;
    this.metrics = metrics;
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
      String limitType = response.getHeaders().getFirst("X-Rate-Limit-Type");
      markRateLimited(parseRetryAfter(retryAfter));
      String endpointTag = endpointTag(request.getURI());
      metrics.recordRateLimited(limitType, endpointTag);
      String msg = "Riot API rate limited (429). type=" + limitType
          + ", retry-after=" + retryAfter + ", uri=" + request.getURI();
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

  /**
   * path 의 가변 segment(PUUID, matchId 등) 를 익명화해 메트릭 cardinality 폭발 방지.
   * 예: {@code /lol/match/v5/matches/by-puuid/abc.../ids} → {@code match.v5.matches.by-puuid.{id}.ids}
   */
  static String endpointTag(URI uri) {
    if (uri == null || uri.getPath() == null) {
      return "unknown";
    }
    return uri.getPath()
        .replaceAll("/[A-Za-z0-9_-]{20,}", "/{id}")
        .replaceFirst("^/lol/", "")
        .replaceFirst("^/riot/", "")
        .replaceFirst("^/+", "")
        .replace('/', '.');
  }
}
