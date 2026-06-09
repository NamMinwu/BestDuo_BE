package com.bestduo_BE.pipeline.application;

import com.bestduo_BE.common.application.PatchVersionService;
import com.bestduo_BE.common.application.port.RiotApiPort;
import com.bestduo_BE.common.domain.model.EffectivePatchContext;
import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.persistence.entity.Summoner;
import com.bestduo_BE.common.infra.persistence.repository.MatchJpaRepository;
import com.bestduo_BE.common.infra.persistence.repository.SummonerJpaRepository;
import com.bestduo_BE.common.infra.riot.budget.DailyBudgetTracker;
import com.bestduo_BE.common.infra.riot.exception.RiotApiException;
import com.bestduo_BE.common.infra.riot.exception.RiotRateLimitedException;
import com.bestduo_BE.config.PipelineProperties;
import com.bestduo_BE.ingest.application.IngestMatchDetail;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * Stage 2 — summoner → match 상세를 inline 으로 수집·저장한다 (ADR-008).
 *
 * <p>summoner 별로 matchIds 를 가져와 그 자리에서 {@link IngestMatchDetail} 로 ingest 한다.
 * 별도 영속 큐(match_queue) 없이 {@code match} 테이블 {@code existsById} 로 dedup 한다.
 *
 * <ul>
 *   <li>CHALLENGER / GRANDMASTER / MASTER: {@code apexTiers} matchIds 수집</li>
 *   <li>그 외: {@code diamondEmerald} matchIds 수집</li>
 * </ul>
 *
 * <p>재시작 안전(ADR-008 §6): patch context 가 있을 때만 수집하고(조건①),
 * summoner 의 모든 매치를 ingest 한 뒤 {@code markMatchIdsCollected} 한다(조건②).
 * 개별 ingest 실패는 1회 시도 후 metric 기록 + skip 한다(옵션 A) — 재수집/참가자 redundancy 가 재시도를 대신한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CollectMatchIdsRunner {

  private final SummonerJpaRepository summonerRepository;
  private final RiotApiPort riotApiPort;
  private final MatchJpaRepository matchRepository;
  private final IngestMatchDetail ingestMatchDetail;
  private final PatchVersionService patchVersionService;
  private final DailyBudgetTracker budgetTracker;
  private final PipelineProperties props;
  private final PipelineMetrics pipelineMetrics;

  // ── public API ──────────────────────────────────────────────────────────

  /**
   * 수집 대기 중인 summoner 가 있으면 true.
   * 예산 소진(canCollect=false) 또는 patch context 부재 시 항상 false(조건①).
   */
  public boolean hasPending() {
    if (!budgetTracker.canCollect()) {
      return false;
    }
    if (patchVersionService.resolveEffectivePatchContext().isEmpty()) {
      return false;
    }
    return !summonerRepository.findMatchIdsPendingSummoners(1).isEmpty();
  }

  /**
   * 한 배치를 처리한다. summoner 를 {@code collectBatchSize} 개 조회해
   * 각각 matchIds 를 수집하고 그 자리에서 inline ingest 한다.
   *
   * @return 배치 결과
   */
  public BatchResult runBatch() {
    if (!budgetTracker.canCollect()) {
      return BatchResult.budgetExhausted();
    }

    EffectivePatchContext ctx = patchVersionService.resolveEffectivePatchContext().orElse(null);
    if (ctx == null) {
      // 조건①: patch context 가 없으면 수집하지 않는다(재시작 시 재수집 재현성 보장).
      return BatchResult.noPending();
    }

    List<Summoner> summoners =
        summonerRepository.findMatchIdsPendingSummoners(props.getCollectBatchSize());
    if (summoners.isEmpty()) {
      return BatchResult.noPending();
    }

    int apiCalls = 0;
    int matchIdsFound = 0;

    for (Summoner summoner : summoners) {
      if (!budgetTracker.canCollect()) {
        break;
      }
      try {
        int found = collectAndIngestForSummoner(summoner, ctx);
        matchIdsFound += found;
        pipelineMetrics.recordMatchesEnqueued(found);
        // 조건②: 모든 매치 ingest 후 collected 표시 → 중간 크래시 시 통째 재처리.
        summonerRepository.markMatchIdsCollected(summoner.getPuuid(), OffsetDateTime.now());
        budgetTracker.recordCollectCall(1);
        apiCalls++;
      } catch (RiotRateLimitedException e) {
        throw e;
      } catch (Exception e) {
        // 수집(matchIds 조회) 실패 시 예산 미차감 — summoner 를 재시도 대상으로 남긴다.
        log.warn("matchIds 수집 실패, 예산 미차감 (재시도 예정): puuid={}", summoner.getPuuid(), e);
      }
    }

    log.info("Stage2 배치 완료: summoners={} apiCalls={} matchIdsFound={}",
        summoners.size(), apiCalls, matchIdsFound);
    return new BatchResult(BatchResult.Type.OK, apiCalls, matchIdsFound);
  }

  // ── private helpers ─────────────────────────────────────────────────────

  private int collectAndIngestForSummoner(Summoner summoner, EffectivePatchContext ctx) {
    int matchCount = matchCountFor(summoner.getLastKnownTier());

    List<String> matchIds = ctx.isInGracePeriod()
        ? riotApiPort.findMatchIdsBetween(
            summoner.getPuuid(), ctx.startTimeEpochSeconds(), ctx.endTimeEpochSeconds(), matchCount)
        : riotApiPort.findMatchIdsSince(
            summoner.getPuuid(), ctx.startTimeEpochSeconds(), matchCount);

    if (matchIds == null || matchIds.isEmpty()) {
      return 0;
    }

    Tier tier = summoner.getLastKnownTier();
    String patch = ctx.patch();
    for (String matchId : matchIds) {
      if (matchRepository.existsById(matchId)) {
        continue; // dedup — 이미 수집된 매치는 API 호출 없이 skip
      }
      try {
        ingestMatchDetail.execute(matchId, tier, patch);
        pipelineMetrics.recordIngestSuccess();
      } catch (RiotRateLimitedException e) {
        throw e; // 429 → 상위 루프 backoff
      } catch (Exception e) {
        // 옵션 A: 1회 시도 후 metric + skip. 재수집/참가자 redundancy 가 재시도를 대신한다.
        pipelineMetrics.recordIngestFailure(classifyIngestFailure(e));
        log.error("ingest 실패 — skip (matchId={})", matchId, e);
      }
    }
    return matchIds.size();
  }

  private int matchCountFor(Tier tier) {
    if (tier != null && tier.isApex()) {
      return props.getTierMatchCount().getApexTiers();
    }
    return props.getTierMatchCount().getDiamondEmerald();
  }

  /** ingest 실패 예외를 저-cardinality reason 태그로 분류한다(NFR-4 alert: auth/server_5xx 식별). */
  private static String classifyIngestFailure(Exception e) {
    Throwable cause = (e instanceof RiotApiException && e.getCause() != null) ? e.getCause() : e;
    if (cause instanceof HttpClientErrorException he) {
      int status = he.getStatusCode().value();
      if (status == 401 || status == 403) {
        return "auth";
      }
      if (status == 404) {
        return "not_found";
      }
      return "client_4xx";
    }
    if (cause instanceof HttpServerErrorException) {
      return "server_5xx";
    }
    if (cause instanceof ResourceAccessException) {
      return "timeout";
    }
    return "other";
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
