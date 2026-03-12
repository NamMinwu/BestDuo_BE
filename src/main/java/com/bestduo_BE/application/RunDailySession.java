package com.bestduo_BE.application;

import com.bestduo_BE.domain.model.SeedBootstrapCommand;
import com.bestduo_BE.monitoring.QueryCountMonitor;
import com.bestduo_BE.monitoring.QueryCountMonitor.QueryStats;
import com.bestduo_BE.infra.riot.budget.BudgetExhaustedException;
import com.bestduo_BE.infra.riot.budget.RiotRequestBudget;
import com.bestduo_BE.infra.riot.exception.RiotRateLimitedException;
import java.time.Duration;
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
  private final QueryCountMonitor queryCountMonitor;

  public Result execute(SessionCommand cmd) {
    ExecutionAccumulator acc = new ExecutionAccumulator(cmd);
    long sessionStartedAt = System.nanoTime();

    try {
      if (cmd.runSeed()) {
        runSeedPhase(cmd, acc);
      }

      if (cmd.runRefresh()) {
        runRefreshPhase(cmd, acc);
      }

      runConsumePhase(cmd, acc);

      Result result = acc.toResult("DONE", "OK");
      logTimings(result, sessionStartedAt, acc.profilingSnapshot());
      return result;

    } catch (BudgetExhaustedException e) {
      log.info("Daily session stopped by budget exhausted: {}", e.getMessage());
      acc.snapshotCurrentPhaseBudget(RiotRequestBudget.remaining());
      Result result = acc.toResult("STOPPED_BUDGET", e.getMessage());
      logTimings(result, sessionStartedAt, acc.profilingSnapshot());
      return result;

    } catch (RiotRateLimitedException e) {
      log.warn("Daily session stopped by 429: {}", e.getMessage());
      acc.snapshotCurrentPhaseBudget(RiotRequestBudget.remaining());
      Result result = acc.toResult("STOPPED_429", e.getMessage());
      logTimings(result, sessionStartedAt, acc.profilingSnapshot());
      return result;

    } finally {
      RiotRequestBudget.clear();
    }
  }

  private void runSeedPhase(SessionCommand cmd, ExecutionAccumulator acc) {
    acc.startSeedPhase();
    RiotRequestBudget.start(cmd.seedBudget());
    queryCountMonitor.reset();
    long phaseStartedAt = System.nanoTime();
    try {
      if (cmd.seedCommand() == null) {
        throw new IllegalArgumentException("Seed command must be provided when runSeed is enabled");
      }
      var seedResult = seedBootstrapRun.execute(cmd.seedCommand());
      acc.addSeedEnqueued(seedResult.matchIdsEnqueued());
      acc.finishSeedPhase(RiotRequestBudget.remaining());
    } finally {
      acc.recordSeedProfiling(System.nanoTime() - phaseStartedAt, queryCountMonitor.snapshotAndReset());
    }
  }

  private void runRefreshPhase(SessionCommand cmd, ExecutionAccumulator acc) {
    acc.startRefreshPhase();
    RiotRequestBudget.start(cmd.refreshBudget());
    queryCountMonitor.reset();
    long phaseStartedAt = System.nanoTime();
    try {
      var refreshResult = refreshBatchRun.execute(cmd.refreshLimit());
      acc.addRefreshEnqueued(refreshResult.matchIdsEnqueued());
      acc.finishRefreshPhase(RiotRequestBudget.remaining());
    } finally {
      acc.recordRefreshProfiling(System.nanoTime() - phaseStartedAt, queryCountMonitor.snapshotAndReset());
    }
  }

  private void runConsumePhase(SessionCommand cmd, ExecutionAccumulator acc) {
    acc.startConsumePhase();
    RiotRequestBudget.start(cmd.consumeBudget());
    queryCountMonitor.reset();
    long phaseStartedAt = System.nanoTime();
    try {
      for (int i = 0; i < cmd.maxConsumeCycles(); i++) {
        var r = matchDetailQueueWorker.execute(cmd.consumeBatchSize());
        acc.recordQueueProcessing(r);

        if (r.processed() == 0) break; // 더 이상 처리할 게 없음
      }

      acc.finishConsumePhase(RiotRequestBudget.remaining());
    } finally {
      acc.recordConsumeProfiling(System.nanoTime() - phaseStartedAt, queryCountMonitor.snapshotAndReset());
    }
  }
  private void logTimings(Result result, long sessionStartedAt, PhaseProfilingSnapshot profilingSnapshot) {
    long totalMillis = Duration.ofNanos(System.nanoTime() - sessionStartedAt).toMillis();
    PhaseProfiling seedProfiling = profilingSnapshot.seed();
    PhaseProfiling refreshProfiling = profilingSnapshot.refresh();
    PhaseProfiling consumeProfiling = profilingSnapshot.consume();

    QueryStats seedSql = seedProfiling.queryStats();
    QueryStats refreshSql = refreshProfiling.queryStats();
    QueryStats consumeSql = consumeProfiling.queryStats();

    log.info(
        "RunDailySession timings status={} message={} totalMs={} seedMs={} seedSqlTotal={} seedSqlSelect={} seedSqlInsert={} seedSqlUpdate={} seedSqlDelete={} refreshMs={} refreshSqlTotal={} refreshSqlSelect={} refreshSqlInsert={} refreshSqlUpdate={} refreshSqlDelete={} consumeMs={} consumeSqlTotal={} consumeSqlSelect={} consumeSqlInsert={} consumeSqlUpdate={} consumeSqlDelete={} queueProcessed={} seedEnqueued={} refreshEnqueued={} picked={} done={} error={} rawCreated={}",
        result.status(),
        result.message(),
        totalMillis,
        seedProfiling.durationMillis(),
        seedSql.total(),
        seedSql.select(),
        seedSql.insert(),
        seedSql.update(),
        seedSql.delete(),
        refreshProfiling.durationMillis(),
        refreshSql.total(),
        refreshSql.select(),
        refreshSql.insert(),
        refreshSql.update(),
        refreshSql.delete(),
        consumeProfiling.durationMillis(),
        consumeSql.total(),
        consumeSql.select(),
        consumeSql.insert(),
        consumeSql.update(),
        consumeSql.delete(),
        result.queueProcessed(),
        result.seedEnqueued(),
        result.refreshEnqueued(),
        result.picked(),
        result.done(),
        result.error(),
        result.rawCreated());
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
    private PhaseProfiling seedProfiling = PhaseProfiling.empty();
    private PhaseProfiling refreshProfiling = PhaseProfiling.empty();
    private PhaseProfiling consumeProfiling = PhaseProfiling.empty();

    ExecutionAccumulator(SessionCommand command) {
      this.command = command;
      this.remainingSeedBudget = command.seedBudget();
      this.remainingRefreshBudget = command.refreshBudget();
      this.remainingConsumeBudget = command.consumeBudget();
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

    void recordSeedProfiling(long durationNanos, QueryStats queryStats) {
      seedProfiling = PhaseProfiling.of(durationNanos, queryStats);
    }

    void recordRefreshProfiling(long durationNanos, QueryStats queryStats) {
      refreshProfiling = PhaseProfiling.of(durationNanos, queryStats);
    }

    void recordConsumeProfiling(long durationNanos, QueryStats queryStats) {
      consumeProfiling = PhaseProfiling.of(durationNanos, queryStats);
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

    PhaseProfilingSnapshot profilingSnapshot() {
      return new PhaseProfilingSnapshot(seedProfiling, refreshProfiling, consumeProfiling);
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

  private record PhaseProfilingSnapshot(PhaseProfiling seed, PhaseProfiling refresh, PhaseProfiling consume) {
    PhaseProfilingSnapshot {
      seed = seed == null ? PhaseProfiling.empty() : seed;
      refresh = refresh == null ? PhaseProfiling.empty() : refresh;
      consume = consume == null ? PhaseProfiling.empty() : consume;
    }
  }
}
