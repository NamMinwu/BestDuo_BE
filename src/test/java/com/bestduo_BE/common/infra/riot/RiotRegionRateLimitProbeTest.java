package com.bestduo_BE.common.infra.riot;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * RiotApiConfig 가 사용하는 두 호스트 — kr.api.riotgames.com (platform), asia.api.riotgames.com
 * (regional) — 이 서로 독립된 rate limit 버킷을 가지는지 실측 검증.
 *
 * <p>실행: RIOT_DEV_API_KEY=RGAPI-... ./gradlew test --tests
 *     "com.bestduo_BE.common.infra.riot.RiotRegionRateLimitProbeTest" -i
 *
 * <p>Dev key는 production personal key와 별개 버킷이므로 파이프라인 중단 불필요.
 */
@EnabledIfEnvironmentVariable(named = "RIOT_DEV_API_KEY", matches = ".+")
@DisplayName("kr.api(platform) vs asia.api(regional) rate limit 독립성 검증 (수동)")
class RiotRegionRateLimitProbeTest {

  // platform: status 엔드포인트는 200 반환
  private static final String KR_URL =
      "https://kr.api.riotgames.com/lol/status/v4/platform-data";

  // regional: status가 없어 account 엔드포인트 사용.
  // 존재하지 않는 riot-id를 호출해 404가 반환되지만, 429만 빼고 모두 rate limit 카운팅됨.
  private static final String ASIA_URL =
      "https://asia.api.riotgames.com"
          + "/riot/account/v1/accounts/by-riot-id/probeNonexistent12345/KR1";

  private static final int RPS_PER_REGION = 18;
  private static final Duration BURST_DURATION = Duration.ofSeconds(1);

  private String apiKey;
  private HttpClient http;
  private ExecutorService executor;

  @BeforeEach
  void setUp() {
    apiKey = System.getenv("RIOT_DEV_API_KEY");
    http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    executor = Executors.newFixedThreadPool(16);
  }

  @AfterEach
  void tearDown() {
    executor.shutdownNow();
  }

  @Test
  @DisplayName("KR 단독 25 rps 부하 시 일부 429 발생해야 함 (KR app limit 존재 확인)")
  void krAlone_above20rps_triggers429() {
    Result kr = burst(KR_URL, 25, BURST_DURATION);
    System.out.println("[KR alone, 25 rps] " + kr);

    assertThat(kr.total()).isGreaterThanOrEqualTo(20);
    assertThat(kr.rateLimited())
        .as("20/s 초과 부하인데 429가 하나도 없으면 한도가 다르거나 실험 설정에 문제")
        .isGreaterThan(0);
  }

  @Test
  @DisplayName("ASIA 단독 25 rps 부하 시 일부 429 발생해야 함 (ASIA app limit 존재 확인)")
  void asiaAlone_above20rps_triggers429() {
    Result asia = burst(ASIA_URL, 25, BURST_DURATION);
    System.out.println("[ASIA alone, 25 rps] " + asia);

    assertThat(asia.total()).isGreaterThanOrEqualTo(20);
    assertThat(asia.rateLimited())
        .as("20/s 초과 부하인데 429가 하나도 없으면 한도가 다르거나 실험 설정에 문제")
        .isGreaterThan(0);
  }

  @Test
  @DisplayName("KR + ASIA 병렬 각 18 rps — 버킷 독립이면 429 거의 없어야 함")
  void krAndAsiaParallel_18rpsEach_noRateLimit() throws Exception {
    CompletableFuture<Result> krFuture =
        CompletableFuture.supplyAsync(
            () -> burst(KR_URL, RPS_PER_REGION, BURST_DURATION), executor);
    CompletableFuture<Result> asiaFuture =
        CompletableFuture.supplyAsync(
            () -> burst(ASIA_URL, RPS_PER_REGION, BURST_DURATION), executor);

    Result kr = krFuture.get();
    Result asia = asiaFuture.get();
    System.out.println("[KR   parallel] " + kr);
    System.out.println("[ASIA parallel] " + asia);

    // 가설: 호스트별 독립 버킷 → 각 18 < 20 이므로 429 ≒ 0
    // 반증: 공유 버킷       → 합산 36/s > 20/s 이므로 약 44%가 429
    assertThat(kr.rateLimited())
        .as("KR 429 — 독립이면 0, 공유면 다수 발생")
        .isLessThanOrEqualTo(2);
    assertThat(asia.rateLimited())
        .as("ASIA 429 — 독립이면 0, 공유면 다수 발생")
        .isLessThanOrEqualTo(2);
  }

  private Result burst(String url, int rps, Duration duration) {
    long intervalNanos = 1_000_000_000L / rps;
    long endNanos = System.nanoTime() + duration.toNanos();
    AtomicInteger notLimited = new AtomicInteger();
    AtomicInteger rateLimited = new AtomicInteger();
    AtomicInteger errors = new AtomicInteger();
    List<CompletableFuture<Void>> inflight = new ArrayList<>();

    long nextSend = System.nanoTime();
    while (System.nanoTime() < endNanos) {
      long now = System.nanoTime();
      if (now < nextSend) {
        LockSupport.parkNanos(nextSend - now);
      }
      nextSend += intervalNanos;

      inflight.add(
          CompletableFuture.runAsync(
              () -> {
                try {
                  HttpRequest req =
                      HttpRequest.newBuilder(URI.create(url))
                          .header("X-Riot-Token", apiKey)
                          .timeout(Duration.ofSeconds(5))
                          .GET()
                          .build();
                  HttpResponse<Void> resp =
                      http.send(req, HttpResponse.BodyHandlers.discarding());
                  int code = resp.statusCode();
                  // 200, 404 등 — rate limit 카운터에는 잡혔지만 throttle은 아님
                  // 429 — 실제 throttle
                  // 401/403/5xx — 인증/서버 오류 (테스트 결함)
                  if (code == 429) {
                    rateLimited.incrementAndGet();
                  } else if ((code >= 200 && code < 300) || code == 404) {
                    notLimited.incrementAndGet();
                  } else {
                    errors.incrementAndGet();
                  }
                } catch (Exception e) {
                  errors.incrementAndGet();
                }
              },
              executor));
    }
    inflight.forEach(CompletableFuture::join);
    return new Result(notLimited.get(), rateLimited.get(), errors.get());
  }

  private record Result(int notLimited, int rateLimited, int errors) {
    int total() {
      return notLimited + rateLimited + errors;
    }

    @Override
    public String toString() {
      return "total="
          + total()
          + ", notLimited="
          + notLimited
          + ", 429="
          + rateLimited
          + ", errors="
          + errors;
    }
  }
}
