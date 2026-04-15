package com.bestduo_BE.ingest.application;

import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.persistence.entity.MatchQueue;
import com.bestduo_BE.config.PipelineProperties;
import com.bestduo_BE.ingest.application.port.MatchQueueDispatcher;
import com.bestduo_BE.common.infra.riot.exception.RiotRateLimitedException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MatchIngestRunner {

  private final MatchQueueDispatcher queue;
  private final IngestMatchDetail ingestMatchDetail;
  private final PipelineProperties props;

  /**
   * tier 필터 + patch+tier 우선순위 pick.
   *
   * @param requestedTier null이면 전체 tier 대상
   * @param currentPatch  현재 패치 (null이면 patch 우선순위 없이 tier 순서만 적용)
   */
  public Result executeWithPriority(int limit, Tier requestedTier, String currentPatch) {
    int recovered = queue.recoverStaleRunning(props.getIngest().getStaleMinutes());
    List<MatchQueueDispatcher.Item> items =
        queue.pickAndLockWithPriority(limit, props.getIngest().getMaxRetry(),
            props.getIngest().getErrorCooldownMinutes(), requestedTier, currentPatch);
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
        log.error("MatchIngestRunner failed. matchId={}", matchId, e);
        queue.markError(matchId, shorten(e.getMessage()));
        processed++;
        error++;
      }
    }

    return new Result(recovered, items.size(), processed, done, error, rawCreated);
  }

  private String shorten(String s) {
    if (s == null) return null;
    return s.length() <= MatchQueue.LAST_ERROR_MAX_LENGTH ? s : s.substring(0, MatchQueue.LAST_ERROR_MAX_LENGTH);
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
