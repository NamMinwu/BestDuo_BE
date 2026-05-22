package com.bestduo_BE.common.infra.riot;

import static org.assertj.core.api.Assertions.assertThat;

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
 * {@link DualWindowRateLimiter} 의 한도별 429 발생률과 server count 분포를 측정한다.
 *
 * <p>측정 절차:
 * <ol>
 *   <li>limiter 한도를 Riot personal/dev key 한도(20/1s + 100/2min) 의 X% 로 설정</li>
 *   <li>해당 limiter 로 1분 동안 부하 발사 (limiter 가 throttle 한 만큼)</li>
 *   <li>응답에서 429 발생 여부 + X-App-Rate-Limit-Count 헤더 캡처</li>
 *   <li>한도 사이 2분 cool down 으로 Riot 장기 윈도우(2min) 회복</li>
 *   <li>한도 50/70/80/90/95% 곡선 출력</li>
 * </ol>
 *
 * <p>실행: {@code RIOT_DEV_API_KEY=RGAPI-... ./gradlew test --tests
 *     "com.bestduo_BE.common.infra.riot.RateLimitHeadroomProbeTest" -i}
 *
 * <p>측정 endpoint 는 match-v5 의 존재하지 않는 matchId (404 응답). method limit (2000/10s) 가
 * 매우 관대하므로 app-level 한도가 binding 된다.
 *
 * <p>Dev key 와 production key 는 별개 rate limit 버킷이므로 파이프라인 중단 불필요.
 * 총 측정 시간 약 13분 (1분 × 5 + 2분 × 4 cool down).
 */
@EnabledIfEnvironmentVariable(named = "RIOT_DEV_API_KEY", matches = ".+")
@DisplayName("DualWindowRateLimiter 한도별 429 발생률 측정 (수동)")
class RateLimitHeadroomProbeTest {

  private static final String PROBE_URL =
      "https://asia.api.riotgames.com/lol/match/v5/matches/KR_1234567890";

  private static final int RIOT_SHORT_LIMIT = 20; // personal/dev key short window
  private static final int RIOT_LONG_LIMIT = 100; // personal/dev key long window
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
  @DisplayName("한도 50/70/80/90/95% 각각 1분 부하 → 429 발생률과 server count 분포")
  @Timeout(value = 20, unit = TimeUnit.MINUTES)
  void measureHeadroomCurve() throws InterruptedException {
    int[] percentages = {50, 70, 80, 90, 95};
    List<Result> results = new ArrayList<>();

    for (int i = 0; i < percentages.length; i++) {
      int pct = percentages[i];
      System.out.printf("%n=== 측정 시작: 한도 %d%% ===%n", pct);

      int shortLimit = Math.max(1, (int) Math.round(RIOT_SHORT_LIMIT * pct / 100.0));
      int longLimit = Math.max(1, (int) Math.round(RIOT_LONG_LIMIT * pct / 100.0));

      DualWindowRateLimiter limiter =
          new DualWindowRateLimiter(
              shortLimit, Duration.ofSeconds(1),
              longLimit, Duration.ofMinutes(2));

      Result r = runMeasurement(limiter, pct, shortLimit, longLimit);
      results.add(r);
      System.out.println(r);

      // 다음 측정 전 cool down (장기 윈도우 회복)
      if (i < percentages.length - 1) {
        System.out.printf(
            "Cool down %d분 (Riot 장기 윈도우 2분 회복 대기)%n", COOL_DOWN.toMinutes());
        Thread.sleep(COOL_DOWN.toMillis());
      }
    }

    printSummary(results);

    // 기본 검증: 50% 한도에선 429 거의 발생 안 해야 함 (마진 50% 면 jitter 영향 무시 가능)
    Result lowest = results.get(0);
    assertThat(lowest.error429Count())
        .as("50%% 한도에서 429 거의 0 이어야 함 (실측 결과 = %d)", lowest.error429Count())
        .isLessThanOrEqualTo(2);
  }

  private Result runMeasurement(
      DualWindowRateLimiter limiter, int pct, int shortLimit, int longLimit) {
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
        limiter.acquire();
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

  /**
   * "X:1,Y:120" 또는 "Y:120,X:1" 형식 모두 지원.
   * 페어의 두 번째 숫자(window 초) 가 작은 쪽이 short window 카운트.
   */
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

  private record AppLimitCount(int shortCount, int longCount) {}

  private void printSummary(List<Result> results) {
    System.out.println();
    System.out.println("=================================================================");
    System.out.println("RateLimit Headroom 측정 결과");
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
          "한도=%d%% (short=%d/1s, long=%d/2min) total=%d 429=%d (%.2f%%) "
              + "max=%d/%d avg=%.1f/%.1f other_err=%d",
          pct, shortLimit, longLimit, totalCalls, error429Count, error429Rate() * 100,
          maxShortCount, maxLongCount, avgShortCount, avgLongCount, otherErrorCount);
    }
  }
}
