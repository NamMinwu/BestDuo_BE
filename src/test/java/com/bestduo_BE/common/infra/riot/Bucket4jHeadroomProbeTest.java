package com.bestduo_BE.common.infra.riot;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Bucket4j composite Bandwidth (token bucket) 의 한도별 429 발생률을 측정한다.
 *
 * <p>{@link RateLimitHeadroomProbeTest} (DualWindowRateLimiter, sliding window log) 와
 * 동일한 부하 조건으로 측정해 두 알고리즘의 거동 차이를 비교하는 것이 목적.
 *
 * <p>이론적 차이:
 * <ul>
 *   <li>sliding window log: 정확한 timestamp 기반, burst 거의 허용 안 함</li>
 *   <li>token bucket: refill rate 기반 근사, 초기/idle 후 bucket 가득 시 burst 허용</li>
 * </ul>
 *
 * <p>실행: {@code RIOT_DEV_API_KEY=RGAPI-... ./gradlew test --tests
 *     "com.bestduo_BE.common.infra.riot.Bucket4jHeadroomProbeTest" -i}
 *
 * <p>총 측정 시간 약 13분 (1분 × 5 한도 + 2분 × 4 cool down).
 */
@EnabledIfEnvironmentVariable(named = "RIOT_DEV_API_KEY", matches = ".+")
@DisplayName("Bucket4j composite 한도별 429 발생률 측정 (수동)")
class Bucket4jHeadroomProbeTest {

  private static final String PROBE_URL =
      "https://asia.api.riotgames.com/lol/match/v5/matches/KR_1234567890";

  private static final int RIOT_SHORT_LIMIT = 20;
  private static final int RIOT_LONG_LIMIT = 100;
  private static final Duration MEASURE_DURATION = Duration.ofMinutes(1);
  private static final Duration COOL_DOWN = Duration.ofMinutes(2);

  private String apiKey;
  private HttpClient http;

  @BeforeEach
  void setUp() {
    apiKey = System.getenv("RIOT_DEV_API_KEY");
    http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  }

  @Test
  @DisplayName("Bucket4j composite: 한도 50/70/80/90/95% 각각 1분 부하 → 429 발생률")
  @Timeout(value = 20, unit = TimeUnit.MINUTES)
  void measureHeadroomCurve() throws InterruptedException {
    int[] percentages = {50, 70, 80, 90, 95};
    List<Result> results = new ArrayList<>();

    for (int i = 0; i < percentages.length; i++) {
      int pct = percentages[i];
      System.out.printf("%n=== [Bucket4j] 측정 시작: 한도 %d%% ===%n", pct);

      int shortLimit = Math.max(1, (int) Math.round(RIOT_SHORT_LIMIT * pct / 100.0));
      int longLimit = Math.max(1, (int) Math.round(RIOT_LONG_LIMIT * pct / 100.0));

      Bucket bucket = buildBucket(shortLimit, longLimit);
      Result r = runMeasurement(bucket, pct, shortLimit, longLimit);
      results.add(r);
      System.out.println(r);

      if (i < percentages.length - 1) {
        System.out.printf(
            "Cool down %d분 (Riot 장기 윈도우 회복 대기)%n", COOL_DOWN.toMinutes());
        Thread.sleep(COOL_DOWN.toMillis());
      }
    }

    printSummary(results);

    // 가장 낮은 한도(50%)에서 429 거의 없어야 함 (sanity check)
    Result lowest = results.get(0);
    assertThat(lowest.error429Count())
        .as("50%% 한도에서 429 발생 = %d (기대: ≤ 2)", lowest.error429Count())
        .isLessThanOrEqualTo(2);
  }

  private Bucket buildBucket(int shortLimit, int longLimit) {
    return Bucket.builder()
        .addLimit(
            Bandwidth.builder()
                .capacity(shortLimit)
                .refillGreedy(shortLimit, Duration.ofSeconds(1))
                .build())
        .addLimit(
            Bandwidth.builder()
                .capacity(longLimit)
                .refillGreedy(longLimit, Duration.ofMinutes(2))
                .build())
        .build();
  }

  private Result runMeasurement(Bucket bucket, int pct, int shortLimit, int longLimit) {
    long endTime = System.currentTimeMillis() + MEASURE_DURATION.toMillis();
    int totalCalls = 0;
    int error429Count = 0;
    int maxShortCount = 0;
    int maxLongCount = 0;
    long shortCountSum = 0;
    long longCountSum = 0;
    int sampleCount = 0;
    int otherErrorCount = 0;
    boolean rawHeaderDumped = false;

    while (System.currentTimeMillis() < endTime
        && !Thread.currentThread().isInterrupted()) {
      try {
        bucket.asBlocking().consume(1);
        totalCalls++;

        HttpRequest req =
            HttpRequest.newBuilder(URI.create(PROBE_URL))
                .header("X-Riot-Token", apiKey)
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        HttpResponse<Void> resp = http.send(req, HttpResponse.BodyHandlers.discarding());
        int code = resp.statusCode();

        if (code == 429) {
          error429Count++;
        } else if (code == 404 || (code >= 200 && code < 300)) {
          String countHeader =
              resp.headers().firstValue("X-App-Rate-Limit-Count").orElse(null);
          if (countHeader != null) {
            if (!rawHeaderDumped) {
              System.out.printf("  [debug] X-App-Rate-Limit-Count raw: %s%n", countHeader);
              rawHeaderDumped = true;
            }
            AppLimitCount c = parseAppLimitCount(countHeader);
            maxShortCount = Math.max(maxShortCount, c.shortCount());
            maxLongCount = Math.max(maxLongCount, c.longCount());
            shortCountSum += c.shortCount();
            longCountSum += c.longCount();
            sampleCount++;
          }
        } else {
          otherErrorCount++;
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      } catch (Exception e) {
        otherErrorCount++;
      }
    }

    double avgShortCount = sampleCount == 0 ? 0 : (double) shortCountSum / sampleCount;
    double avgLongCount = sampleCount == 0 ? 0 : (double) longCountSum / sampleCount;
    return new Result(
        pct, shortLimit, longLimit, totalCalls, error429Count,
        maxShortCount, maxLongCount, avgShortCount, avgLongCount, otherErrorCount);
  }

  private AppLimitCount parseAppLimitCount(String header) {
    String[] pairs = header.split(",");
    int firstCount = Integer.parseInt(pairs[0].split(":")[0]);
    int firstWindow = Integer.parseInt(pairs[0].split(":")[1]);
    if (pairs.length < 2) {
      return new AppLimitCount(firstCount, firstCount);
    }
    int secondCount = Integer.parseInt(pairs[1].split(":")[0]);
    int secondWindow = Integer.parseInt(pairs[1].split(":")[1]);
    if (firstWindow <= secondWindow) {
      return new AppLimitCount(firstCount, secondCount);
    }
    return new AppLimitCount(secondCount, firstCount);
  }

  private void printSummary(List<Result> results) {
    System.out.println();
    System.out.println("=================================================================");
    System.out.println("[Bucket4j composite] RateLimit Headroom 측정 결과");
    System.out.println("=================================================================");
    System.out.printf(
        "%-6s %-7s %-7s %-7s %-15s %-9s %-9s%n",
        "한도", "short", "long", "호출", "429", "max(s/L)", "avg(s/L)");
    for (Result r : results) {
      System.out.printf(
          "%-6s %-7d %-7d %-7d %-15s %d/%-7d %.1f/%-5.1f%n",
          r.pct() + "%",
          r.shortLimit(),
          r.longLimit(),
          r.totalCalls(),
          r.error429Count() + " (" + String.format("%.1f%%", r.error429Rate() * 100) + ")",
          r.maxShortCount(), r.maxLongCount(),
          r.avgShortCount(), r.avgLongCount());
    }
    System.out.println("=================================================================");
    System.out.println("(s = short 윈도우 카운트, L = long 윈도우 카운트, max/avg)");
    System.out.println();
  }

  private record AppLimitCount(int shortCount, int longCount) {}

  private record Result(
      int pct,
      int shortLimit,
      int longLimit,
      int totalCalls,
      int error429Count,
      int maxShortCount,
      int maxLongCount,
      double avgShortCount,
      double avgLongCount,
      int otherErrorCount) {
    double error429Rate() {
      return totalCalls == 0 ? 0 : (double) error429Count / totalCalls;
    }

    @Override
    public String toString() {
      return String.format(
          "[Bucket4j] 한도=%d%% (short=%d/1s, long=%d/2min) total=%d 429=%d (%.2f%%) "
              + "max=%d/%d avg=%.1f/%.1f other_err=%d",
          pct, shortLimit, longLimit, totalCalls, error429Count, error429Rate() * 100,
          maxShortCount, maxLongCount, avgShortCount, avgLongCount, otherErrorCount);
    }
  }
}
