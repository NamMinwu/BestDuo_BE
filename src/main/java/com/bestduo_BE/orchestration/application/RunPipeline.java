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
public class RunPipeline {

  private final SeedBootstrapExecutor seedBootstrapExecutor;
  private final RefreshBatchExecutor refreshBatchExecutor;
  private final MatchIngestWorker matchIngestWorker;
  private final QueryCountMonitor queryCountMonitor;

  public Result execute(RunCommand cmd) {
    ExecutionAccumulator acc = new ExecutionAccumulator(cmd);
    long runStartedAt = System.nanoTime();

    try {
      if (cmd.runSeed()) {
        runSeedPhase(cmd, acc);
      }

      if (cmd.runRefresh()) {
        runRefreshPhase(cmd, acc);
      }

      runIngestPhase(cmd, acc);

      Result result = acc.toResult("DONE", "OK");
      logTimings(result, runStartedAt, acc.profilingSnapshot());
      return result;

    } catch (BudgetExhaustedException e) {
      log.info("Daily run stopped by budget exhausted: {}", e.getMessage());
      acc.snapshotCurrentPhaseBudget(0);
      Result result = acc.toResult("STOPPED_BUDGET", e.getMessage());
      logTimings(result, runStartedAt, acc.profilingSnapshot());
      return result;

    } catch (RiotRateLimitedException e) {
      log.warn("Daily run stopped by 429: {}", e.getMessage());
      acc.snapshotCurrentPhaseBudget(RiotRequestBudget.remaining());
      Result result = acc.toResult("STOPPED_429", e.getMessage());
      logTimings(result, runStartedAt, acc.profilingSnapshot());
      return result;

    } finally {
      RiotRequestBudget.clear();
    }
  }

  private void runSeedPhase(RunCommand cmd, ExecutionAccumulator acc) {
    acc.startSeedPhase();
    RiotRequestBudget.start(cmd.seedBudget());
    queryCountMonitor.reset();
    long phaseStartedAt = System.nanoTime();
    try {
      if (cmd.seedCommand() == null) {
        throw new IllegalArgumentException("Seed command must be provided when runSeed is enabled");
      }
      var seedResult = seedBootstrapExecutor.execute(cmd.seedCommand());
      acc.addSeedEnqueued(seedResult.matchIdsEnqueued());
      acc.finishSeedPhase(RiotRequestBudget.remaining());
    } finally {
      acc.recordSeedProfiling(System.nanoTime() - phaseStartedAt, queryCountMonitor.snapshotAndReset());
    }
  }

  private void runRefreshPhase(RunCommand cmd, ExecutionAccumulator acc) {
    acc.startRefreshPhase();
    RiotRequestBudget.start(cmd.refreshBudget());
    queryCountMonitor.reset();
    long phaseStartedAt = System.nanoTime();
    try {
      var refreshResult = refreshBatchExecutor.execute(cmd.refreshLimit());
      acc.addRefreshEnqueued(refreshResult.matchIdsEnqueued());
      acc.finishRefreshPhase(RiotRequestBudget.remaining());
    } finally {
      acc.recordRefreshProfiling(System.nanoTime() - phaseStartedAt, queryCountMonitor.snapshotAndReset());
    }
  }

  private void runIngestPhase(RunCommand cmd, ExecutionAccumulator acc) {
    acc.startIngestPhase();
    RiotRequestBudget.start(cmd.ingestBudget());
    queryCountMonitor.reset();
    long phaseStartedAt = System.nanoTime();
    try {
      for (int i = 0; i < cmd.maxIngestCycles(); i++) {
        var r = matchIngestWorker.execute(cmd.ingestBatchSize());
        acc.recordQueueProcessing(r);

        if (r.processed() == 0) break; // 더 이상 처리할 게 없음
      }

      acc.finishIngestPhase(RiotRequestBudget.remaining());
    } finally {
      acc.recordIngestProfiling(System.nanoTime() - phaseStartedAt, queryCountMonitor.snapshotAndReset());
    }
  }
  private void logTimings(Result result, long runStartedAt, PhaseProfilingSnapshot profilingSnapshot) {
    long totalMillis = Duration.ofNanos(System.nanoTime() - runStartedAt).toMillis();
    PhaseProfiling seedProfiling = profilingSnapshot.seed();
    PhaseProfiling refreshProfiling = profilingSnapshot.refresh();
    PhaseProfiling ingestProfiling = profilingSnapshot.ingest();

    QueryStats seedSql = seedProfiling.queryStats();
    QueryStats refreshSql = refreshProfiling.queryStats();
    QueryStats ingestSql = ingestProfiling.queryStats();

    log.info(
        "RunPipeline timings status={} message={} totalMs={} seedMs={} seedSqlTotal={} seedSqlSelect={} seedSqlInsert={} seedSqlUpdate={} seedSqlDelete={} refreshMs={} refreshSqlTotal={} refreshSqlSelect={} refreshSqlInsert={} refreshSqlUpdate={} refreshSqlDelete={} ingestMs={} ingestSqlTotal={} ingestSqlSelect={} ingestSqlInsert={} ingestSqlUpdate={} ingestSqlDelete={} queueProcessed={} seedEnqueued={} refreshEnqueued={} picked={} done={} error={} rawCreated={}",
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
        ingestProfiling.durationMillis(),
        ingestSql.total(),
        ingestSql.select(),
        ingestSql.insert(),
        ingestSql.update(),
        ingestSql.delete(),
        result.queueProcessed(),
        result.seedEnqueued(),
        result.refreshEnqueued(),
        result.picked(),
        result.done(),
        result.error(),
        result.rawCreated());
  }

  public record RunCommand(
      int budgetTotal,
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

  private static final class ExecutionAccumulator {
    private final RunCommand command;
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
    private boolean seedPhaseActive;
    private boolean refreshPhaseActive;
    private boolean ingestPhaseStarted;
    private PhaseProfiling seedProfiling = PhaseProfiling.empty();
    private PhaseProfiling refreshProfiling = PhaseProfiling.empty();
    private PhaseProfiling ingestProfiling = PhaseProfiling.empty();

    ExecutionAccumulator(RunCommand command) {
      this.command = command;
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

    void startIngestPhase() {
      ingestPhaseStarted = true;
    }

    void finishIngestPhase(int remainingBudget) {
      remainingIngestBudget = remainingBudget;
    }

    void recordSeedProfiling(long durationNanos, QueryStats queryStats) {
      seedProfiling = PhaseProfiling.of(durationNanos, queryStats);
    }

    void recordRefreshProfiling(long durationNanos, QueryStats queryStats) {
      refreshProfiling = PhaseProfiling.of(durationNanos, queryStats);
    }

    void recordIngestProfiling(long durationNanos, QueryStats queryStats) {
      ingestProfiling = PhaseProfiling.of(durationNanos, queryStats);
    }

    void snapshotCurrentPhaseBudget(int snapshot) {
      if (seedPhaseActive) {
        remainingSeedBudget = snapshot;
      } else if (refreshPhaseActive) {
        remainingRefreshBudget = snapshot;
      } else if (ingestPhaseStarted) {
        remainingIngestBudget = snapshot;
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

    PhaseProfilingSnapshot profilingSnapshot() {
      return new PhaseProfilingSnapshot(seedProfiling, refreshProfiling, ingestProfiling);
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

  private record PhaseProfilingSnapshot(PhaseProfiling seed, PhaseProfiling refresh, PhaseProfiling ingest) {
    PhaseProfilingSnapshot {
      seed = seed == null ? PhaseProfiling.empty() : seed;
      refresh = refresh == null ? PhaseProfiling.empty() : refresh;
      ingest = ingest == null ? PhaseProfiling.empty() : ingest;
    }
  }
}
