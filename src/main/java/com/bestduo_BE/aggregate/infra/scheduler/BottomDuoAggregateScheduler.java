package com.bestduo_BE.aggregate.infra.scheduler;

import com.bestduo_BE.aggregate.application.AggregateBottomDuoFromMatch;
import com.bestduo_BE.aggregate.application.CleanupOldPatches;
import com.bestduo_BE.aggregate.application.ComputeBottomDuoRanking;
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
 * 직접 계산해 upsert 한 뒤, 같은 tier 에 대해 ranking 까지 계산해 PENDING 행을 RANKED 로 승격시킨다.
 *
 * <p>대상 tier 는 CHALLENGER ~ EMERALD 5개로 한정한다. 한 tier 의 aggregate 가 실패하면 해당 tier 의
 * ranking 은 건너뛰지만, 나머지 tier 와 cleanup 단계는 계속 진행한다.
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
  private final ComputeBottomDuoRanking rankingUseCase;
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
      boolean aggregateSucceeded = false;
      try {
        AggregateBottomDuoFromMatch.Result result =
            fromMatchUseCase.execute(patchVersion, tier, true);
        log.info(
            "[AggregateScheduler] tier={} processed={} statKeys={} matchupKeys={}"
                + " statTotalGames={} matchupTotalGames={} upserted={}/{}",
            tier, result.matchesProcessed(), result.statKeys(), result.matchupKeys(),
            result.statTotalGames(), result.matchupTotalGames(),
            result.statUpserted(), result.matchupUpserted());
        aggregateSucceeded = true;
      } catch (Exception ex) {
        log.error("[AggregateScheduler] aggregate failed tier={}", tier, ex);
      }

      if (!aggregateSucceeded) {
        continue;
      }

      try {
        ComputeBottomDuoRanking.Result rankingResult =
            rankingUseCase.execute(patchVersion, tier.name());
        log.info("[AggregateScheduler] ranking tier={} updatedRows={}",
            tier, rankingResult.updatedRows());
      } catch (Exception ex) {
        log.error("[AggregateScheduler] ranking failed tier={}", tier, ex);
      }
    }

    try {
      CleanupOldPatches.Result cleanupResult = cleanupUseCase.execute();
      log.info("[AggregateScheduler] cleanup stat={} matchup={} keep={}",
          cleanupResult.statDeleted(), cleanupResult.matchupDeleted(),
          cleanupResult.keepPatches());
    } catch (Exception ex) {
      log.error("[AggregateScheduler] cleanup failed", ex);
    }

    log.info("[AggregateScheduler] done patch={}", patchVersion);
  }
}
