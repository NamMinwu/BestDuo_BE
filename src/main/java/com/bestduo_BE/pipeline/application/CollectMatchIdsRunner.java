package com.bestduo_BE.pipeline.application;

import com.bestduo_BE.common.application.PatchVersionService;
import com.bestduo_BE.common.application.port.MatchQueueEnqueuer;
import com.bestduo_BE.common.application.port.RiotApiPort;
import com.bestduo_BE.common.domain.model.EffectivePatchContext;
import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.persistence.entity.Summoner;
import com.bestduo_BE.common.infra.persistence.repository.SummonerJpaRepository;
import com.bestduo_BE.common.infra.riot.budget.DailyBudgetTracker;
import com.bestduo_BE.common.infra.riot.exception.RiotRateLimitedException;
import com.bestduo_BE.config.PipelineProperties;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Stage 2 — summoner → match_queue.
 *
 * <p>seeded_at이 있고 match_ids_collected_at이 null이거나 seeded_at 이전인 summoner를
 * seeded_at 최신 순으로 조회해 matchIds를 수집하고 match_queue에 enqueue한다.
 *
 * <ul>
 *   <li>CHALLENGER / GRANDMASTER / MASTER: {@code apexTiers} matchIds 수집</li>
 *   <li>그 외: {@code diamondEmerald} matchIds 수집</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CollectMatchIdsRunner {

  private final SummonerJpaRepository summonerRepository;
  private final RiotApiPort riotApiPort;
  private final MatchQueueEnqueuer matchQueueEnqueuer;
  private final PatchVersionService patchVersionService;
  private final DailyBudgetTracker budgetTracker;
  private final PipelineProperties props;

  // ── public API ──────────────────────────────────────────────────────────

  /**
   * matchIds 수집 대기 중인 summoner가 있으면 true.
   * 예산 소진 시 항상 false.
   */
  public boolean hasPending() {
    if (!budgetTracker.canCollect()) {
      return false;
    }
    return !summonerRepository.findMatchIdsPendingSummoners(1).isEmpty();
  }

  /**
   * 한 배치를 처리한다.
   * summoner를 {@code collectBatchSize}개 조회해 각각 matchIds를 수집하고 enqueue한다.
   *
   * @return 배치 결과
   */
  public BatchResult runBatch() {
    if (!budgetTracker.canCollect()) {
      return BatchResult.budgetExhausted();
    }

    List<Summoner> summoners =
        summonerRepository.findMatchIdsPendingSummoners(props.getCollectBatchSize());
    if (summoners.isEmpty()) {
      return BatchResult.noPending();
    }

    EffectivePatchContext ctx = patchVersionService.resolveEffectivePatchContext().orElse(null);

    int apiCalls = 0;
    int matchIdsQueued = 0;

    for (Summoner summoner : summoners) {
      if (!budgetTracker.canCollect()) {
        break;
      }
      try {
        int queued = collectForSummoner(summoner, ctx);
        matchIdsQueued += queued;
        summonerRepository.markMatchIdsCollected(summoner.getPuuid(), OffsetDateTime.now());
        budgetTracker.recordCollectCall(1);
        apiCalls++;
      } catch (RiotRateLimitedException e) {
        throw e;
      } catch (Exception e) {
        // 실패 시 예산 미차감 — summoner를 재시도 대상으로 남긴다.
        log.warn("matchIds 수집 실패, 예산 미차감 (재시도 예정): puuid={}", summoner.getPuuid(), e);
      }
    }

    log.info("Stage2 배치 완료: summoners={} apiCalls={} matchIdsQueued={}",
        summoners.size(), apiCalls, matchIdsQueued);
    return new BatchResult(BatchResult.Type.OK, apiCalls, matchIdsQueued);
  }

  // ── private helpers ─────────────────────────────────────────────────────

  private int collectForSummoner(Summoner summoner, EffectivePatchContext ctx) {
    int matchCount = matchCountFor(summoner.getLastKnownTier());

    List<String> matchIds;
    if (ctx == null) {
      matchIds = riotApiPort.findRecentMatchIds(summoner.getPuuid(), matchCount);
    } else if (ctx.isInGracePeriod()) {
      matchIds = riotApiPort.findMatchIdsBetween(
          summoner.getPuuid(), ctx.startTimeEpochSeconds(), ctx.endTimeEpochSeconds(), matchCount);
    } else {
      matchIds = riotApiPort.findMatchIdsSince(
          summoner.getPuuid(), ctx.startTimeEpochSeconds(), matchCount);
    }

    if (matchIds == null || matchIds.isEmpty()) {
      return 0;
    }

    String patch = ctx != null ? ctx.patch() : null;
    Tier tier = summoner.getLastKnownTier();
    matchQueueEnqueuer.enqueueAllIdempotent(matchIds, tier, props.getCollectPriority(), patch);
    return matchIds.size();
  }

  private int matchCountFor(Tier tier) {
    if (tier != null && tier.isApex()) {
      return props.getTierMatchCount().getApexTiers();
    }
    return props.getTierMatchCount().getDiamondEmerald();
  }

  // ── Result types ────────────────────────────────────────────────────────

  public record BatchResult(Type type, int apiCalls, int matchIdsQueued) {

    public enum Type {
      OK,
      BUDGET_EXHAUSTED,
      NO_PENDING
    }

    public static BatchResult budgetExhausted() {
      return new BatchResult(Type.BUDGET_EXHAUSTED, 0, 0);
    }

    public static BatchResult noPending() {
      return new BatchResult(Type.NO_PENDING, 0, 0);
    }
  }
}
