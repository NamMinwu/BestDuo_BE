package com.bestduo_BE.pipeline.application;

import com.bestduo_BE.common.application.PatchVersionService;
import com.bestduo_BE.common.application.port.MatchIdsFinder;
import com.bestduo_BE.common.application.port.MatchQueueEnqueuer;
import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.persistence.entity.Summoner;
import com.bestduo_BE.common.infra.persistence.repository.SummonerJpaRepository;
import com.bestduo_BE.common.infra.riot.budget.DailyBudgetTracker;
import com.bestduo_BE.common.infra.riot.exception.RiotRateLimitedException;
import com.bestduo_BE.config.PipelineProperties;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
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

  private static final Set<Tier> APEX_TIERS =
      Set.of(Tier.CHALLENGER, Tier.GRANDMASTER, Tier.MASTER);

  private final SummonerJpaRepository summonerRepository;
  private final MatchIdsFinder matchIdsFinder;
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

    String currentPatch = patchVersionService.currentPatchVersion().orElse(null);
    Long patchStartTime = patchVersionService.currentPatchStartTimeEpochSeconds().orElse(null);

    int apiCalls = 0;
    int matchIdsQueued = 0;

    for (Summoner summoner : summoners) {
      if (!budgetTracker.canCollect()) {
        break;
      }
      try {
        int queued = collectForSummoner(summoner, currentPatch, patchStartTime);
        matchIdsQueued += queued;
        summonerRepository.markMatchIdsCollected(summoner.getPuuid(), OffsetDateTime.now());
        budgetTracker.recordCollectCall(1);
        apiCalls++;
      } catch (RiotRateLimitedException e) {
        throw e;
      } catch (Exception e) {
        // API 호출은 발생했으므로 예산은 차감한다. summoner는 재시도 대상으로 남긴다.
        budgetTracker.recordCollectCall(1);
        apiCalls++;
        log.warn("matchIds 수집 실패 (재시도 예정): puuid={}", summoner.getPuuid(), e);
      }
    }

    log.info("Stage2 배치 완료: summoners={} apiCalls={} matchIdsQueued={}",
        summoners.size(), apiCalls, matchIdsQueued);
    return new BatchResult(BatchResult.Type.OK, apiCalls, matchIdsQueued);
  }

  // ── private helpers ─────────────────────────────────────────────────────

  private int collectForSummoner(Summoner summoner, String currentPatch, Long patchStartTime) {
    int matchCount = matchCountFor(summoner.getLastKnownTier());

    List<String> matchIds;
    if (patchStartTime != null) {
      matchIds = matchIdsFinder.findMatchIdsSince(summoner.getPuuid(), patchStartTime, matchCount);
    } else {
      matchIds = matchIdsFinder.findRecentMatchIds(summoner.getPuuid(), matchCount);
    }

    if (matchIds == null || matchIds.isEmpty()) {
      return 0;
    }

    Tier tier = summoner.getLastKnownTier() != null ? summoner.getLastKnownTier() : Tier.ALL_TIERS;
    matchQueueEnqueuer.enqueueAllIdempotent(matchIds, tier, props.getCollectPriority(), currentPatch);
    return matchIds.size();
  }

  private int matchCountFor(Tier tier) {
    if (tier != null && APEX_TIERS.contains(tier)) {
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
