package com.bestduo_BE.orchestration.application;

import com.bestduo_BE.common.domain.model.SeedBootstrapCommand;
import com.bestduo_BE.ingest.application.MatchIngestWorker;
import com.bestduo_BE.common.infra.riot.budget.BudgetExhaustedException;
import com.bestduo_BE.common.infra.riot.budget.RiotRequestBudget;
import com.bestduo_BE.common.infra.riot.exception.RiotRateLimitedException;
import com.bestduo_BE.monitoring.QueryCountMonitor;
import com.bestduo_BE.monitoring.QueryCountMonitor.QueryStats;
import com.bestduo_BE.refresh.application.RefreshBatchExecutor;
import com.bestduo_BE.seed.application.SeedBootstrapExecutor;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExecutionPipeline {

  private enum Phase {
    SEED,
    REFRESH,
    INGEST
  }

  private final SeedBootstrapExecutor seedBootstrapExecutor;
  private final RefreshBatchExecutor refreshBatchExecutor;
  private final MatchIngestWorker matchIngestWorker;
  private final QueryCountMonitor queryCountMonitor;

  public Result execute(ExecutionCommand cmd) {
    ExecutionState state = new ExecutionState(cmd);
    long runStartedAt = System.nanoTime();

    try {
      executeEnabledPhases(cmd, state);
      return finishSuccess(state, runStartedAt);

    } catch (BudgetExhaustedException e) {
      return finishBudgetStopped(state, runStartedAt, e);

    } catch (RiotRateLimitedException e) {
      return finishRateLimited(state, runStartedAt, e);

    } finally {
      RiotRequestBudget.clear();
    }
  }

  private void executeEnabledPhases(ExecutionCommand cmd, ExecutionState state) {
    if (cmd.runSeed()) {
      runSeedPhase(cmd, state);
    }

    if (cmd.runRefresh()) {
      runRefreshPhase(cmd, state);
    }

    runIngestPhase(cmd, state);
  }

  private Result finishSuccess(ExecutionState state, long runStartedAt) {
    Result result = state.toResult("DONE", "OK");
    logTimings(result, runStartedAt, state);
    return result;
  }

  private Result finishBudgetStopped(ExecutionState state, long runStartedAt, BudgetExhaustedException e) {
    log.info("Daily run stopped by budget exhausted: {}", e.getMessage());
    state.snapshotCurrentPhaseBudget(0);
    Result result = state.toResult("STOPPED_BUDGET", e.getMessage());
    logTimings(result, runStartedAt, state);
    return result;
  }

  private Result finishRateLimited(ExecutionState state, long runStartedAt, RiotRateLimitedException e) {
    log.warn("Daily run stopped by 429: {}", e.getMessage());
    state.snapshotCurrentPhaseBudget(RiotRequestBudget.remaining());
    Result result = state.toResult("STOPPED_429", e.getMessage());
    logTimings(result, runStartedAt, state);
    return result;
  }

  private void runSeedPhase(ExecutionCommand cmd, ExecutionState state) {
    executePhase(Phase.SEED, cmd.seedBudget(), state, () -> {
      if (cmd.seedCommand() == null) {
        throw new IllegalArgumentException("Seed command must be provided when runSeed is enabled");
      }
      var seedResult = seedBootstrapExecutor.execute(cmd.seedCommand());
      state.addSeedEnqueued(seedResult.matchIdsEnqueued());
    });
  }

  private void runRefreshPhase(ExecutionCommand cmd, ExecutionState state) {
    executePhase(Phase.REFRESH, cmd.refreshBudget(), state, () -> {
      var refreshResult = refreshBatchExecutor.execute(cmd.refreshLimit());
      state.addRefreshEnqueued(refreshResult.matchIdsEnqueued());
    });
  }

  private void runIngestPhase(ExecutionCommand cmd, ExecutionState state) {
    executePhase(Phase.INGEST, cmd.ingestBudget(), state, () -> {
      for (int i = 0; i < cmd.maxIngestCycles(); i++) {
        var result = matchIngestWorker.execute(cmd.ingestBatchSize());
        state.recordQueueProcessing(result);

        if (result.processed() == 0) {
          break;
        }
      }
    });
  }

  private void executePhase(Phase phase, int phaseBudget, ExecutionState state, Runnable action) {
    state.startPhase(phase);
    RiotRequestBudget.start(phaseBudget);
    queryCountMonitor.reset();
    long phaseStartedAt = System.nanoTime();

    try {
      action.run();
      state.finishPhase(phase, RiotRequestBudget.remaining());
    } finally {
      state.recordProfiling(phase, System.nanoTime() - phaseStartedAt, queryCountMonitor.snapshotAndReset());
    }
  }

  private void logTimings(Result result, long runStartedAt, ExecutionState state) {
    long totalMillis = Duration.ofNanos(System.nanoTime() - runStartedAt).toMillis();
    PhaseProfiling seedProfiling = state.seedProfiling();
    PhaseProfiling refreshProfiling = state.refreshProfiling();
    PhaseProfiling ingestProfiling = state.ingestProfiling();

    QueryStats seedSql = seedProfiling.queryStats();
    QueryStats refreshSql = refreshProfiling.queryStats();
    QueryStats ingestSql = ingestProfiling.queryStats();

    log.info(
        "ExecutionPipeline summary status={} message={} totalMs={} seedMs={} refreshMs={} ingestMs={} seedSql={} refreshSql={} ingestSql={} queueProcessed={} seedEnqueued={} refreshEnqueued={} picked={} done={} error={} rawCreated={}",
        result.status(),
        result.message(),
        totalMillis,
        seedProfiling.durationMillis(),
        refreshProfiling.durationMillis(),
        ingestProfiling.durationMillis(),
        seedSql.total(),
        refreshSql.total(),
        ingestSql.total(),
        result.queueProcessed(),
        result.seedEnqueued(),
        result.refreshEnqueued(),
        result.picked(),
        result.done(),
        result.error(),
        result.rawCreated());
  }

  public record ExecutionCommand(
      int seedBudget,
      int refreshBudget,
      int ingestBudget,
      boolean runSeed,
      boolean runRefresh,
      int refreshLimit,
      int ingestBatchSize,
      int maxIngestCycles,
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

  private static final class ExecutionState {
    private int seedEnqueued;
    private int refreshEnqueued;
    private int queueProcessed;
    private int picked;
    private int done;
    private int error;
    private int rawCreated;
    private int remainingSeedBudget;
    private int remainingRefreshBudget;
    private int remainingIngestBudget;
    private Phase currentPhase;
    private PhaseProfiling seedProfiling = PhaseProfiling.empty();
    private PhaseProfiling refreshProfiling = PhaseProfiling.empty();
    private PhaseProfiling ingestProfiling = PhaseProfiling.empty();

    ExecutionState(ExecutionCommand command) {
      this.remainingSeedBudget = command.seedBudget();
      this.remainingRefreshBudget = command.refreshBudget();
      this.remainingIngestBudget = command.ingestBudget();
    }

    void addSeedEnqueued(int value) {
      seedEnqueued += value;
    }

    void addRefreshEnqueued(int value) {
      refreshEnqueued += value;
    }

    void recordQueueProcessing(MatchIngestWorker.Result result) {
      queueProcessed += result.processed();
      picked += result.picked();
      done += result.done();
      error += result.error();
      rawCreated += result.rawCreated();
    }

    void startPhase(Phase phase) {
      currentPhase = phase;
    }

    void finishPhase(Phase phase, int remainingBudget) {
      switch (phase) {
        case SEED -> remainingSeedBudget = remainingBudget;
        case REFRESH -> remainingRefreshBudget = remainingBudget;
        case INGEST -> remainingIngestBudget = remainingBudget;
      }
      currentPhase = null;
    }

    void recordProfiling(Phase phase, long durationNanos, QueryStats queryStats) {
      PhaseProfiling profiling = PhaseProfiling.of(durationNanos, queryStats);
      switch (phase) {
        case SEED -> seedProfiling = profiling;
        case REFRESH -> refreshProfiling = profiling;
        case INGEST -> ingestProfiling = profiling;
      }
    }

    void snapshotCurrentPhaseBudget(int snapshot) {
      if (currentPhase == null) {
        return;
      }

      switch (currentPhase) {
        case SEED -> remainingSeedBudget = snapshot;
        case REFRESH -> remainingRefreshBudget = snapshot;
        case INGEST -> remainingIngestBudget = snapshot;
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
      int total = remainingSeedBudget + remainingRefreshBudget + remainingIngestBudget;
      return Math.max(0, total);
    }

    PhaseProfiling seedProfiling() {
      return seedProfiling;
    }

    PhaseProfiling refreshProfiling() {
      return refreshProfiling;
    }

    PhaseProfiling ingestProfiling() {
      return ingestProfiling;
    }
  }

  private record PhaseProfiling(long durationNanos, QueryStats queryStats) {
    PhaseProfiling {
      durationNanos = Math.max(0, durationNanos);
      queryStats = queryStats == null ? QueryStats.empty() : queryStats;
    }

    static PhaseProfiling empty() {
      return new PhaseProfiling(0L, QueryStats.empty());
    }

    static PhaseProfiling of(long durationNanos, QueryStats queryStats) {
      return new PhaseProfiling(durationNanos, queryStats);
    }

    long durationMillis() {
      return Duration.ofNanos(durationNanos).toMillis();
    }
  }

}
