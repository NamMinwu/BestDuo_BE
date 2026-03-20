package com.bestduo_BE.orchestration.application;

import com.bestduo_BE.config.DailyRunProperties;
import com.bestduo_BE.common.domain.model.SeedBootstrapCommand;
import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.orchestration.infra.persistence.entity.RunLog;
import com.bestduo_BE.orchestration.infra.persistence.repository.RunLogJpaRepository;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DailyRunExecutor {

  private final RunLogJpaRepository runLogJpaRepository;
  private final ExecuteDailyRun executeDailyRun;
  private final DailyRunProperties dailyRunProperties;

  public RunLog.RunResult run(
      int budgetTotal,
      double seedRatio,
      double refreshRatio,
      int ingestLimitPerCycle,
      int maxIngestCycles,
      int refreshLimit,
      Tier tier
  ) {
    OffsetDateTime started = OffsetDateTime.now();

    int seedBudget = (int) Math.floor(budgetTotal * seedRatio);
    int refreshBudget = (int) Math.floor(budgetTotal * refreshRatio);
    int ingestBudget = Math.max(0, budgetTotal - seedBudget - refreshBudget);

    ExecuteDailyRun.RunCommand command = buildCommand(
        budgetTotal,
        seedBudget,
        refreshBudget,
        ingestBudget,
        ingestLimitPerCycle,
        maxIngestCycles,
        refreshLimit,
        tier
    );

    log.info("command: {}", command);

    try {
      ExecuteDailyRun.Result runResult = executeDailyRun.execute(command);
      return persistResult(buildSuccessResult(started, budgetTotal, seedBudget, refreshBudget, ingestBudget, runResult));

    } catch (Exception e) {
      persistResult(buildErrorResult(started, budgetTotal, seedBudget, refreshBudget, ingestBudget, e));
      throw e; // 운영에서 원하면 result 반환으로 바꿔도 됨
    }
  }

  private RunLog.RunResult buildSuccessResult(
      OffsetDateTime started,
      int budgetTotal,
      int seedBudget,
      int refreshBudget,
      int ingestBudget,
      ExecuteDailyRun.Result runResult
  ) {
    OffsetDateTime ended = OffsetDateTime.now();
    return new RunLog.RunResult(
        started,
        ended,
        runResult.status(),
        budgetTotal,
        seedBudget,
        refreshBudget,
        ingestBudget,
        runResult.seedEnqueued(),
        runResult.refreshEnqueued(),
        runResult.picked(),
        runResult.done(),
        runResult.error(),
        runResult.rawCreated(),
        runResult.message()
    );
  }

  private RunLog.RunResult buildErrorResult(
      OffsetDateTime started,
      int budgetTotal,
      int seedBudget,
      int refreshBudget,
      int ingestBudget,
      Exception e
  ) {
    OffsetDateTime ended = OffsetDateTime.now();
    return new RunLog.RunResult(
        started,
        ended,
        "ERROR",
        budgetTotal,
        seedBudget,
        refreshBudget,
        ingestBudget,
        0,
        0,
        0,
        0,
        0,
        0,
        e.getMessage()
    );
  }

  private RunLog.RunResult persistResult(RunLog.RunResult result) {
    runLogJpaRepository.save(RunLog.of(result));
    return result;
  }

  private ExecuteDailyRun.RunCommand buildCommand(
      int budgetTotal,
      int seedBudget,
      int refreshBudget,
      int ingestBudget,
      int ingestLimitPerCycle,
      int maxIngestCycles,
      int refreshLimit,
      Tier tier
  ) {
    SeedBootstrapCommand seedCommand = null;
    if (dailyRunProperties.isRunSeed()) {
      DailyRunProperties.Seed seedProps = dailyRunProperties.getSeed();
      Tier resolvedTier = tier != null ? tier : seedProps.getSeedTier();
      String riotTier = tier != null ? tier.name() : seedProps.getTier();
      seedCommand = new SeedBootstrapCommand(
          seedProps.getQueue(),
          riotTier,
          seedProps.getDivision(),
          resolvedTier,
          seedProps.getStartPage(),
          seedProps.getEndPage(),
          seedProps.getMatchesPerPuuid(),
          seedProps.getMaxEntries()
      );
    }

    return new ExecuteDailyRun.RunCommand(
        budgetTotal,
        seedBudget,
        refreshBudget,
        ingestBudget,
        dailyRunProperties.isRunSeed(),
        dailyRunProperties.isRunRefresh(),
        refreshLimit,
        ingestLimitPerCycle,
        maxIngestCycles,
        seedCommand
    );
  }
}
