package com.bestduo_BE.application;

import com.bestduo_BE.domain.model.SeedBootstrapCommand;
import com.bestduo_BE.infra.riot.budget.BudgetExhaustedException;
import com.bestduo_BE.infra.riot.budget.RiotRequestBudget;
import com.bestduo_BE.infra.riot.exception.RiotRateLimitedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RunDailySession {

  private final SeedBootstrapRun seedBootstrapRun;
  private final RefreshBatchRun refreshBatchRun;
  private final MatchDetailQueueWorker matchDetailQueueWorker;

  public Result execute(SessionCommand cmd) {
    RiotRequestBudget.start(cmd.budgetRequests());

    try {
      int seedEnqueued = 0;
      int refreshEnqueued = 0;
      int queueProcessed = 0;

      // 1) Seed (optional)
      if (cmd.runSeed()) {
        var seedResult = seedBootstrapRun.execute(cmd.seedCommand());
        // seedResult에 “enqueued”를 넣어두면 더 좋음 (없으면 생략)
      }

      // 2) Refresh (optional)
      if (cmd.runRefresh()) {
        var refreshResult = refreshBatchRun.execute(cmd.refreshLimit());
        refreshEnqueued += refreshResult.matchIdsEnqueued();
      }

      // 3) Consume queue (핵심)
      // 예산이 남는 한 여러 번 돌릴 수 있음(단, 무한루프 말고 안전장치로 maxCycles)
      for (int i = 0; i < cmd.maxConsumeCycles(); i++) {
        var r = matchDetailQueueWorker.execute(cmd.consumeBatchSize());
        queueProcessed += r.processed();

        if (r.processed() == 0) break; // 더 이상 처리할 게 없음
      }

      return new Result(
          RiotRequestBudget.remaining(),
          seedEnqueued,
          refreshEnqueued,
          queueProcessed,
          "DONE"
      );

    } catch (BudgetExhaustedException e) {
      log.info("Daily session stopped by budget exhausted: {}", e.getMessage());
      return new Result(
          0, 0, 0, 0,
          "STOPPED_BUDGET"
      );

    } catch (RiotRateLimitedException e) {
      // 개발키 보호 모드: 즉시 종료
      log.warn("Daily session stopped by 429: {}", e.getMessage());
      return new Result(
          RiotRequestBudget.remaining(), 0, 0, 0,
          "STOPPED_429"
      );

    } finally {
      RiotRequestBudget.clear();
    }
  }

  public record SessionCommand(
      int budgetRequests,
      boolean runSeed,
      boolean runRefresh,
      int refreshLimit,
      int consumeBatchSize,
      int maxConsumeCycles,
      SeedBootstrapCommand seedCommand
  ) {}

  public record Result(
      int remainingBudget,
      int seedEnqueued,
      int refreshEnqueued,
      int queueProcessed,
      String status
  ) {}
}
