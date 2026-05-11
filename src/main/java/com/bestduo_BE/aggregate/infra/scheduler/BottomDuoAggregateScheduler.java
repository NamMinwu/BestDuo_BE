package com.bestduo_BE.aggregate.infra.scheduler;

import com.bestduo_BE.aggregate.application.AggregateBottomDuoMatchup;
import com.bestduo_BE.aggregate.application.AggregateBottomDuoStats;
import com.bestduo_BE.common.application.PatchVersionService;
import com.bestduo_BE.common.domain.model.Tier;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 cron 시각에 최신 패치 기준으로 stat aggregate(+ ranking) × 5개 tier와 matchup aggregate를
 * 순차 실행한다.
 *
 * <p>대상 tier는 CHALLENGER ~ EMERALD 5개로 한정한다. 한 tier에서 예외가 발생해도 나머지 tier와
 * matchup 작업은 계속 진행한다 — 부분 실패가 전체 운영을 멈추지 않도록 한다.
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
  private final AggregateBottomDuoStats statUseCase;
  private final AggregateBottomDuoMatchup matchupUseCase;

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
        AggregateBottomDuoStats.Result result = statUseCase.execute(patchVersion, tier);
        log.info("[AggregateScheduler] stat tier={} affected={} rankings={}",
            tier, result.affectedRows(), result.rankingsUpdated());
      } catch (Exception ex) {
        log.error("[AggregateScheduler] stat aggregate failed tier={}", tier, ex);
      }
    }

    try {
      AggregateBottomDuoMatchup.Result matchupResult = matchupUseCase.execute();
      log.info("[AggregateScheduler] matchup affected={}", matchupResult.affectedRows());
    } catch (Exception ex) {
      log.error("[AggregateScheduler] matchup aggregate failed", ex);
    }

    log.info("[AggregateScheduler] done patch={}", patchVersion);
  }
}
