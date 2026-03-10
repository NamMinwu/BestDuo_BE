package com.bestduo_BE.application;

import com.bestduo_BE.config.DailySessionProperties;
import com.bestduo_BE.domain.model.SeedBootstrapCommand;
import com.bestduo_BE.infra.persistence.entity.SessionRunLog;
import com.bestduo_BE.infra.persistence.repository.SessionRunLogJpaRepository;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionRunner {

  private final SessionRunLogJpaRepository sessionRunLogJpaRepository;
  private final RunDailySession runDailySession;
  private final DailySessionProperties dailySessionProperties;

  public SessionRunLog.SessionResult run(int budgetTotal, double seedRatio, double refreshRatio, int consumeLimitPerCycle, int maxConsumeCycles) {
    OffsetDateTime started = OffsetDateTime.now();

    int seedBudget = (int) Math.floor(budgetTotal * seedRatio);
    int refreshBudget = (int) Math.floor(budgetTotal * refreshRatio);
    int consumeBudget = Math.max(0, budgetTotal - seedBudget - refreshBudget);

    RunDailySession.SessionCommand command = buildCommand(
        budgetTotal,
        seedBudget,
        refreshBudget,
        consumeBudget,
        consumeLimitPerCycle,
        maxConsumeCycles
    );

    log.info("command: {}", command);

    try {
      RunDailySession.Result runResult = runDailySession.execute(command);
      return persistResult(buildSuccessResult(started, budgetTotal, seedBudget, refreshBudget, consumeBudget, runResult));

    } catch (Exception e) {
      persistResult(buildErrorResult(started, budgetTotal, seedBudget, refreshBudget, consumeBudget, e));
      throw e; // 운영에서 원하면 result 반환으로 바꿔도 됨
    }
  }

  private SessionRunLog.SessionResult buildSuccessResult(
      OffsetDateTime started,
      int budgetTotal,
      int seedBudget,
      int refreshBudget,
      int consumeBudget,
      RunDailySession.Result runResult
  ) {
    OffsetDateTime ended = OffsetDateTime.now();
    return new SessionRunLog.SessionResult(
        started,
        ended,
        runResult.status(),
        budgetTotal,
        seedBudget,
        refreshBudget,
        consumeBudget,
        runResult.seedEnqueued(),
        runResult.refreshEnqueued(),
        runResult.picked(),
        runResult.done(),
        runResult.error(),
        runResult.rawCreated(),
        runResult.message()
    );
  }

  private SessionRunLog.SessionResult buildErrorResult(
      OffsetDateTime started,
      int budgetTotal,
      int seedBudget,
      int refreshBudget,
      int consumeBudget,
      Exception e
  ) {
    OffsetDateTime ended = OffsetDateTime.now();
    return new SessionRunLog.SessionResult(
        started,
        ended,
        "ERROR",
        budgetTotal,
        seedBudget,
        refreshBudget,
        consumeBudget,
        0,
        0,
        0,
        0,
        0,
        0,
        e.getMessage()
    );
  }

  private SessionRunLog.SessionResult persistResult(SessionRunLog.SessionResult result) {
    sessionRunLogJpaRepository.save(SessionRunLog.of(result));
    return result;
  }

  private RunDailySession.SessionCommand buildCommand(int budgetTotal, int seedBudget, int refreshBudget, int consumeBudget, int consumeLimitPerCycle, int maxConsumeCycles) {
    SeedBootstrapCommand seedCommand = null;
    if (dailySessionProperties.isRunSeed()) {
      DailySessionProperties.Seed seedProps = dailySessionProperties.getSeed();
      seedCommand = new SeedBootstrapCommand(
          seedProps.getQueue(),
          seedProps.getTier(),
          seedProps.getDivision(),
          seedProps.getSeedTier(),
          seedProps.getStartPage(),
          seedProps.getEndPage(),
          seedProps.getMatchesPerPuuid()
      );
    }

    return new RunDailySession.SessionCommand(
        budgetTotal,
        seedBudget,
        refreshBudget,
        consumeBudget,
        dailySessionProperties.isRunSeed(),
        dailySessionProperties.isRunRefresh(),
        dailySessionProperties.getRefreshLimit(),
        consumeLimitPerCycle,
        maxConsumeCycles,
        seedCommand
    );
  }
}
