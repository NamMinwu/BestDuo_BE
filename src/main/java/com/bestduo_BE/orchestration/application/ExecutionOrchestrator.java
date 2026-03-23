package com.bestduo_BE.orchestration.application;

import com.bestduo_BE.config.DailyRunProperties;
import com.bestduo_BE.common.domain.model.SeedBootstrapCommand;
import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.orchestration.infra.persistence.entity.ExecutionLog;
import com.bestduo_BE.orchestration.infra.persistence.repository.ExecutionLogJpaRepository;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExecutionOrchestrator {

  private final ExecutionLogJpaRepository executionLogJpaRepository;
  private final ExecutionPipeline executionPipeline;
  private final DailyRunProperties dailyRunProperties;

  public ExecutionLog.ExecutionResult run(
      int budgetTotal,
      double seedRatio,
      double refreshRatio,
      int ingestLimitPerCycle,
      int maxIngestCycles,
      int refreshLimit,
      Tier tier
  ) {
    OffsetDateTime startedAt = OffsetDateTime.now();
    BudgetAllocation budgets = allocateBudgets(budgetTotal, seedRatio, refreshRatio);
    ExecutionPipeline.ExecutionCommand command = buildCommand(
        budgets,
        ingestLimitPerCycle,
        maxIngestCycles,
        refreshLimit,
        tier
    );

    log.info("command: {}", command);

    try {
      return executeAndPersist(startedAt, budgetTotal, budgets, command);

    } catch (Exception e) {
      persistResult(buildErrorResult(startedAt, budgetTotal, budgets, e));
      throw e;
    }
  }

  private BudgetAllocation allocateBudgets(int budgetTotal, double seedRatio, double refreshRatio) {
    int seedBudget = (int) Math.floor(budgetTotal * seedRatio);
    int refreshBudget = (int) Math.floor(budgetTotal * refreshRatio);
    int ingestBudget = Math.max(0, budgetTotal - seedBudget - refreshBudget);
    return new BudgetAllocation(seedBudget, refreshBudget, ingestBudget);
  }

  private ExecutionLog.ExecutionResult executeAndPersist(
      OffsetDateTime startedAt,
      int budgetTotal,
      BudgetAllocation budgets,
      ExecutionPipeline.ExecutionCommand command
  ) {
    ExecutionPipeline.Result pipelineResult = executionPipeline.execute(command);
    return persistResult(buildSuccessResult(startedAt, budgetTotal, budgets, pipelineResult));
  }

  private ExecutionLog.ExecutionResult buildSuccessResult(
      OffsetDateTime startedAt,
      int budgetTotal,
      BudgetAllocation budgets,
      ExecutionPipeline.Result pipelineResult
  ) {
    OffsetDateTime endedAt = OffsetDateTime.now();
    return new ExecutionLog.ExecutionResult(
        startedAt,
        endedAt,
        pipelineResult.status(),
        budgetTotal,
        budgets.seedBudget(),
        budgets.refreshBudget(),
        budgets.ingestBudget(),
        pipelineResult.seedEnqueued(),
        pipelineResult.refreshEnqueued(),
        pipelineResult.picked(),
        pipelineResult.done(),
        pipelineResult.error(),
        pipelineResult.rawCreated(),
        pipelineResult.message()
    );
  }

  private ExecutionLog.ExecutionResult buildErrorResult(
      OffsetDateTime startedAt,
      int budgetTotal,
      BudgetAllocation budgets,
      Exception e
  ) {
    OffsetDateTime endedAt = OffsetDateTime.now();
    return new ExecutionLog.ExecutionResult(
        startedAt,
        endedAt,
        "ERROR",
        budgetTotal,
        budgets.seedBudget(),
        budgets.refreshBudget(),
        budgets.ingestBudget(),
        0,
        0,
        0,
        0,
        0,
        0,
        e.getMessage()
    );
  }

  private ExecutionLog.ExecutionResult persistResult(ExecutionLog.ExecutionResult result) {
    executionLogJpaRepository.save(ExecutionLog.of(result));
    return result;
  }

  private ExecutionPipeline.ExecutionCommand buildCommand(
      BudgetAllocation budgets,
      int ingestLimitPerCycle,
      int maxIngestCycles,
      int refreshLimit,
      Tier tier
  ) {
    SeedBootstrapCommand seedCommand = buildSeedCommand(tier);
    Tier requestedTier = seedCommand != null
        ? seedCommand.seedTier()
        : (tier != null ? tier : Tier.ALL_TIERS);
    return new ExecutionPipeline.ExecutionCommand(
        budgets.seedBudget(),
        budgets.refreshBudget(),
        budgets.ingestBudget(),
        dailyRunProperties.isRunSeed(),
        dailyRunProperties.isRunRefresh(),
        refreshLimit,
        ingestLimitPerCycle,
        maxIngestCycles,
        seedCommand,
        requestedTier
    );
  }

  private SeedBootstrapCommand buildSeedCommand(Tier tier) {
    if (!dailyRunProperties.isRunSeed()) {
      return null;
    }

    DailyRunProperties.Seed seedProps = dailyRunProperties.getSeed();
    Tier resolvedTier = tier != null ? tier : seedProps.getSeedTier();
    String riotTier = tier != null ? tier.name() : seedProps.getTier();
    return new SeedBootstrapCommand(
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

  private record BudgetAllocation(int seedBudget, int refreshBudget, int ingestBudget) {}
}
