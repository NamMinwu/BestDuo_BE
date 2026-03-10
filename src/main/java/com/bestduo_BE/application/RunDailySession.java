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
    ExecutionAccumulator acc = new ExecutionAccumulator(cmd);

    try {
      if (acc.seedPhaseEnabled()) {
        runSeedAndRefreshPhases(cmd, acc);
      }

      runConsumePhase(cmd, acc);
      return acc.toResult("DONE", "OK");

    } catch (BudgetExhaustedException e) {
      log.info("Daily session stopped by budget exhausted: {}", e.getMessage());
      acc.snapshotCurrentPhaseBudget(RiotRequestBudget.remaining());
      return acc.toResult("STOPPED_BUDGET", e.getMessage());

    } catch (RiotRateLimitedException e) {
      log.warn("Daily session stopped by 429: {}", e.getMessage());
      acc.snapshotCurrentPhaseBudget(RiotRequestBudget.remaining());
      return acc.toResult("STOPPED_429", e.getMessage());

    } finally {
      RiotRequestBudget.clear();
    }
  }

  private void runSeedAndRefreshPhases(SessionCommand cmd, ExecutionAccumulator acc) {
    if (cmd.runSeed()) {
      acc.startSeedPhase();
      RiotRequestBudget.start(cmd.seedBudget());
      if (cmd.seedCommand() == null) {
        throw new IllegalArgumentException("Seed command must be provided when runSeed is enabled");
      }
      var seedResult = seedBootstrapRun.execute(cmd.seedCommand());
      acc.addSeedEnqueued(seedResult.matchIdsEnqueued());
      acc.finishSeedPhase(RiotRequestBudget.remaining());
    }

    if (cmd.runRefresh()) {
      acc.startRefreshPhase();
      RiotRequestBudget.start(cmd.refreshBudget());
      var refreshResult = refreshBatchRun.execute(cmd.refreshLimit());
      acc.addRefreshEnqueued(refreshResult.matchIdsEnqueued());
      acc.finishRefreshPhase(RiotRequestBudget.remaining());
    }
  }

  private void runConsumePhase(SessionCommand cmd, ExecutionAccumulator acc) {
    acc.startConsumePhase();
    RiotRequestBudget.start(cmd.consumeBudget());

    for (int i = 0; i < cmd.maxConsumeCycles(); i++) {
      var r = matchDetailQueueWorker.execute(cmd.consumeBatchSize());
      acc.recordQueueProcessing(r);

      if (r.processed() == 0) break; // 더 이상 처리할 게 없음
    }

    acc.finishConsumePhase(RiotRequestBudget.remaining());
  }

  public record SessionCommand(
      int budgetTotal,
      int seedBudget,
      int refreshBudget,
      int consumeBudget,
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
      int picked,
      int done,
      int error,
      int rawCreated,
      String status,
      String message
  ) {}

  private static final class ExecutionAccumulator {
    private final SessionCommand command;
    private int seedEnqueued;
    private int refreshEnqueued;
    private int queueProcessed;
    private int picked;
    private int done;
    private int error;
    private int rawCreated;
    private int remainingSeedBudget;
    private int remainingRefreshBudget;
    private int remainingConsumeBudget;
    private boolean seedPhaseActive;
    private boolean refreshPhaseActive;
    private boolean consumePhaseStarted;

    ExecutionAccumulator(SessionCommand command) {
      this.command = command;
      this.remainingSeedBudget = command.seedBudget();
      this.remainingRefreshBudget = command.refreshBudget();
      this.remainingConsumeBudget = command.consumeBudget();
    }

    boolean seedPhaseEnabled() {
      return command.runSeed() || command.runRefresh();
    }

    void addSeedEnqueued(int value) {
      seedEnqueued += value;
    }

    void addRefreshEnqueued(int value) {
      refreshEnqueued += value;
    }

    void recordQueueProcessing(MatchDetailQueueWorker.Result result) {
      queueProcessed += result.processed();
      picked += result.picked();
      done += result.done();
      error += result.error();
      rawCreated += result.rawCreated();
    }

    void startSeedPhase() {
      seedPhaseActive = true;
    }

    void finishSeedPhase(int remainingBudget) {
      remainingSeedBudget = remainingBudget;
      seedPhaseActive = false;
    }

    void startRefreshPhase() {
      refreshPhaseActive = true;
    }

    void finishRefreshPhase(int remainingBudget) {
      remainingRefreshBudget = remainingBudget;
      refreshPhaseActive = false;
    }

    void startConsumePhase() {
      consumePhaseStarted = true;
    }

    void finishConsumePhase(int remainingBudget) {
      remainingConsumeBudget = remainingBudget;
    }

    void snapshotCurrentPhaseBudget(int snapshot) {
      if (seedPhaseActive) {
        remainingSeedBudget = snapshot;
      } else if (refreshPhaseActive) {
        remainingRefreshBudget = snapshot;
      } else if (consumePhaseStarted) {
        remainingConsumeBudget = snapshot;
      }
    }

    Result toResult(String status, String message) {
      return toResult(status, message, totalRemainingBudget());
    }

    Result toResult(String status, String message, int remainingBudgetOverride) {
      return new Result(
          remainingBudgetOverride,
          seedEnqueued,
          refreshEnqueued,
          queueProcessed,
          picked,
          done,
          error,
          rawCreated,
          status,
          message
      );
    }

    private int totalRemainingBudget() {
      int total = remainingSeedBudget + remainingRefreshBudget + remainingConsumeBudget;
      return Math.max(0, total);
    }
  }
}
