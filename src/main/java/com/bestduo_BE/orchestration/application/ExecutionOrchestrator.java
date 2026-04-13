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

  /**
   * refreshRatio and refreshLimit are kept for API/DB compatibility but no longer used.
   */
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
    BudgetAllocation budgets = allocateBudgets(budgetTotal, seedRatio);
    ExecutionPipeline.ExecutionCommand command = buildCommand(budgets, ingestLimitPerCycle, maxIngestCycles, tier);

    log.info("command: {}", command);

    try {
      return executeAndPersist(startedAt, budgetTotal, budgets, command);
    } catch (Exception e) {
      persistResult(buildErrorResult(startedAt, budgetTotal, budgets, e));
      throw e;
    }
  }

  private BudgetAllocation allocateBudgets(int budgetTotal, double seedRatio) {
    int seedBudget = (int) Math.floor(budgetTotal * seedRatio);
    int ingestBudget = Math.max(0, budgetTotal - seedBudget);
    return new BudgetAllocation(seedBudget, ingestBudget);
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
    return new ExecutionLog.ExecutionResult(
        startedAt,
        OffsetDateTime.now(),
        pipelineResult.status(),
        budgetTotal,
        budgets.seedBudget(),
        0,   // refreshBudget: removed
        budgets.ingestBudget(),
        pipelineResult.seedEnqueued(),
        0,   // refreshEnqueued: removed
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
    return new ExecutionLog.ExecutionResult(
        startedAt,
        OffsetDateTime.now(),
        "ERROR",
        budgetTotal,
        budgets.seedBudget(),
        0,
        budgets.ingestBudget(),
        0, 0, 0, 0, 0, 0,
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
      Tier tier
  ) {
    SeedBootstrapCommand seedCommand = buildSeedCommand(tier);
    Tier requestedTier = seedCommand != null
        ? seedCommand.seedTier()
        : (tier != null ? tier : Tier.ALL_TIERS);
    return new ExecutionPipeline.ExecutionCommand(
        budgets.seedBudget(),
        budgets.ingestBudget(),
        dailyRunProperties.isRunSeed(),
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

  private record BudgetAllocation(int seedBudget, int ingestBudget) {}
}
