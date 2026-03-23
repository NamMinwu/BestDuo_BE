package com.bestduo_BE.ingest.application;

import com.bestduo_BE.ingest.application.port.MatchQueueDispatcher;
import com.bestduo_BE.common.infra.riot.budget.BudgetExhaustedException;
import com.bestduo_BE.common.infra.riot.exception.RiotRateLimitedException;
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
   *
   * - stale RUNNING 먼저 복구
   * - Budget/429는 세션 종료 신호: unlockToReady 후 예외 전파
   */
  public Result execute(int limit) {
    int recovered = queue.recoverStaleRunning(STALE_MINUTES);

    int processed = 0;
    int rawCreated = 0;
    int done = 0;
    int error = 0;

    var items = queue.pickAndLock(limit, MAX_RETRY, ERROR_COOLDOWN_MINUTES);

    for (MatchQueueDispatcher.Item item : items) {
      String matchId = item.matchId();

      try {
        var r = ingestMatchDetail.execute(matchId, item.tier());
        rawCreated += r.rawCreated();

        queue.markDone(matchId);
        processed++;
        done++;

      } catch (BudgetExhaustedException | RiotRateLimitedException e) {
        // ✅ 실패가 아니라 오늘 세션 종료(키 보호/예산 소진)
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
