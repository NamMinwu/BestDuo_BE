package com.bestduo_BE.ingest.application;

import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.ingest.application.port.MatchQueueDispatcher;
import com.bestduo_BE.common.infra.riot.exception.RiotRateLimitedException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MatchIngestWorker {

  private static final int STALE_MINUTES = 10;
  private static final int ERROR_COOLDOWN_MINUTES = 10;
  private static final int MAX_RETRY = 2;

  private final MatchQueueDispatcher queue;
  private final IngestMatchDetail ingestMatchDetail;

  /**
   * match_queue에서 (READY 우선, 남는 자리 ERROR 재시도)로 뽑아
   * Phase1(IngestMatchDetail)만 수행한다.
   */
  public Result execute(int limit) {
    return execute(limit, Tier.ALL_TIERS);
  }

  public Result execute(int limit, Tier requestedTier) {
    int recovered = queue.recoverStaleRunning(STALE_MINUTES);
    List<MatchQueueDispatcher.Item> items =
        queue.pickAndLock(limit, MAX_RETRY, ERROR_COOLDOWN_MINUTES, requestedTier);
    return processItems(items, recovered);
  }

  /**
   * tier 필터 + patch+tier 우선순위 pick.
   *
   * @param requestedTier null 또는 ALL_TIERS이면 전체 tier 대상
   * @param currentPatch  현재 패치 (null이면 patch 우선순위 없이 tier 순서만 적용)
   */
  public Result executeWithPriority(int limit, Tier requestedTier, String currentPatch) {
    int recovered = queue.recoverStaleRunning(STALE_MINUTES);
    List<MatchQueueDispatcher.Item> items =
        queue.pickAndLockWithPriority(limit, MAX_RETRY, ERROR_COOLDOWN_MINUTES, requestedTier, currentPatch);
    return processItems(items, recovered);
  }

  // ── private ────────────────────────────────────────────────────────────

  private Result processItems(List<MatchQueueDispatcher.Item> items, int recovered) {
    int processed = 0;
    int rawCreated = 0;
    int done = 0;
    int error = 0;

    for (MatchQueueDispatcher.Item item : items) {
      String matchId = item.matchId();

      try {
        var r = ingestMatchDetail.execute(matchId, item.tier(), item.patch());
        rawCreated += r.rawCreated();

        queue.markDone(matchId);
        processed++;
        done++;

      } catch (RiotRateLimitedException e) {
        queue.unlockToReady(matchId);
        throw e;

      } catch (Exception e) {
        log.error("MatchIngestWorker failed. matchId={}", matchId, e);
        queue.markError(matchId, shorten(e.getMessage()));
        processed++;
        error++;
      }
    }

    return new Result(recovered, items.size(), processed, done, error, rawCreated);
  }

  private String shorten(String s) {
    if (s == null) return null;
    return s.length() <= 500 ? s : s.substring(0, 500);
  }

  public record Result(
      int recoveredStale,
      int picked,
      int processed,
      int done,
      int error,
      int rawCreated
  ) {}
}
