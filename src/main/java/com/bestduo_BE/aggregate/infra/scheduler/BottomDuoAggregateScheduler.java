package com.bestduo_BE.aggregate.infra.scheduler;

import com.bestduo_BE.aggregate.application.AggregateBottomDuoFromMatch;
import com.bestduo_BE.aggregate.application.CleanupOldPatches;
import com.bestduo_BE.common.application.PatchVersionService;
import com.bestduo_BE.common.domain.model.Tier;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 cron 시각에 최신 패치 기준으로 stat + matchup aggregate 를 {@code match.payload_json} 으로부터
 * 직접 계산해 upsert 한다 ({@link AggregateBottomDuoFromMatch} 단일 경로).
 *
 * <p>대상 tier 는 CHALLENGER ~ EMERALD 5개로 한정한다. 한 tier 에서 예외가 발생해도 나머지 tier 와
 * cleanup 단계는 계속 진행한다.
 *
 * <p>이전에는 {@code bottom_duo_raw} 위 SQL GROUP BY/self-join 으로 같은 결과를 만들었으나,
 * 향후 lane 조합 확장을 위해 단일 경로로 통일했다. 검증 절차는 {@code docs/aggregate-from-match-verification.md}
 * 참고.
 */
@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "aggregate.scheduler.enabled", havingValue = "true")
public class BottomDuoAggregateScheduler {

  private static final List<Tier> SCHEDULED_TIERS = List.of(
      Tier.CHALLENGER,
      Tier.GRANDMASTER,
      Tier.MASTER,
      Tier.DIAMOND,
      Tier.EMERALD
  );

  private final PatchVersionService patchVersionService;
  private final AggregateBottomDuoFromMatch fromMatchUseCase;
  private final CleanupOldPatches cleanupUseCase;

  @Scheduled(cron = "${aggregate.scheduler.cron:0 0 4 * * *}", zone = "Asia/Seoul")
  public void run() {
    var latestPatch = patchVersionService.currentPatch();
    if (latestPatch.isEmpty()) {
      log.warn("[AggregateScheduler] No patch registered — skipping run");
      return;
    }

    String patchVersion = latestPatch.get().getPatch();
    log.info("[AggregateScheduler] start patch={}", patchVersion);

    for (Tier tier : SCHEDULED_TIERS) {
      try {
        AggregateBottomDuoFromMatch.Result result =
            fromMatchUseCase.execute(patchVersion, tier, true);
        log.info(
            "[AggregateScheduler] tier={} processed={} statKeys={} matchupKeys={}"
                + " statTotalGames={} matchupTotalGames={} upserted={}/{}",
            tier, result.matchesProcessed(), result.statKeys(), result.matchupKeys(),
            result.statTotalGames(), result.matchupTotalGames(),
            result.statUpserted(), result.matchupUpserted());
      } catch (Exception ex) {
        log.error("[AggregateScheduler] aggregate failed tier={}", tier, ex);
      }
    }

    try {
      CleanupOldPatches.Result cleanupResult = cleanupUseCase.execute();
      log.info("[AggregateScheduler] cleanup stat={} matchup={} raw={} keep={}",
          cleanupResult.statDeleted(), cleanupResult.matchupDeleted(),
          cleanupResult.rawDeleted(), cleanupResult.keepPatches());
    } catch (Exception ex) {
      log.error("[AggregateScheduler] cleanup failed", ex);
    }

    log.info("[AggregateScheduler] done patch={}", patchVersion);
  }
}
