package com.bestduo_BE.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.bestduo_BE.domain.model.SeedBootstrapCommand;
import com.bestduo_BE.domain.model.Tier;
import com.bestduo_BE.infra.riot.budget.BudgetExhaustedException;
import com.bestduo_BE.infra.riot.budget.RiotRequestBudget;
import com.bestduo_BE.infra.riot.exception.RiotRateLimitedException;
import com.bestduo_BE.monitoring.QueryCountMonitor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RunDailySessionTest {

  @Mock
  private SeedBootstrapRun seedBootstrapRun;

  @Mock
  private RefreshBatchRun refreshBatchRun;

  @Mock
  private MatchDetailQueueWorker matchDetailQueueWorker;

  private RunDailySession useCase;
  private QueryCountMonitor queryCountMonitor;

  private final SeedBootstrapCommand seedCommand = new SeedBootstrapCommand(
      "RANKED_SOLO", "GOLD", "I", Tier.GOLD, 1, 1, 20
  );

  @BeforeEach
  void setUp() {
    queryCountMonitor = new QueryCountMonitor();
    useCase = new RunDailySession(seedBootstrapRun, refreshBatchRun, matchDetailQueueWorker, queryCountMonitor);
  }

  @AfterEach
  void tearDown() {
    RiotRequestBudget.clear();
  }

  @Test
  void executeRunsEnabledPhasesAndAggregatesCounts() {
    RunDailySession.SessionCommand cmd = new RunDailySession.SessionCommand(
        120, 20, 20, 80, true, true, 5, 10, 3, seedCommand
    );
    given(seedBootstrapRun.execute(seedCommand))
        .willReturn(new SeedBootstrapRun.SeedBootstrapResult(1, 2, 3, 4, 5));
    given(refreshBatchRun.execute(5))
        .willReturn(new RefreshBatchRun.Result(2, 2, 0, 7));
    given(matchDetailQueueWorker.execute(10))
        .willReturn(
            new MatchDetailQueueWorker.Result(0, 2, 5, 5, 0, 5),
            new MatchDetailQueueWorker.Result(0, 0, 0, 0, 0, 0)
        );

    RunDailySession.Result result = useCase.execute(cmd);

    verify(seedBootstrapRun).execute(seedCommand);
    verify(refreshBatchRun).execute(5);
    verify(matchDetailQueueWorker, times(2)).execute(10);
    assertThat(result.status()).isEqualTo("DONE");
    assertThat(result.remainingBudget()).isEqualTo(120);
    assertThat(result.seedEnqueued()).isEqualTo(5);
    assertThat(result.refreshEnqueued()).isEqualTo(7);
    assertThat(result.queueProcessed()).isEqualTo(5);
    assertThat(result.picked()).isEqualTo(2);
    assertThat(result.done()).isEqualTo(5);
    assertThat(result.error()).isZero();
    assertThat(result.rawCreated()).isEqualTo(5);
    assertThat(result.message()).isEqualTo("OK");
  }

  @Test
  void executeStopsWhenBudgetExhausted() {
    RunDailySession.SessionCommand cmd = new RunDailySession.SessionCommand(
        50, 0, 0, 50, false, false, 0, 10, 1, seedCommand
    );
    given(matchDetailQueueWorker.execute(10))
        .willThrow(new BudgetExhaustedException("budget gone"));

    RunDailySession.Result result = useCase.execute(cmd);

    verify(seedBootstrapRun, never()).execute(seedCommand);
    verify(refreshBatchRun, never()).execute(0);
    assertThat(result.status()).isEqualTo("STOPPED_BUDGET");
    assertThat(result.remainingBudget()).isZero();
    assertThat(result.refreshEnqueued()).isZero();
    assertThat(result.queueProcessed()).isZero();
    assertThat(result.message()).isEqualTo("budget gone");
  }

  @Test
  void executeStopsWhenRateLimited() {
    RunDailySession.SessionCommand cmd = new RunDailySession.SessionCommand(
        80, 0, 0, 80, false, false, 0, 10, 1, seedCommand
    );
    given(matchDetailQueueWorker.execute(10))
        .willThrow(new RiotRateLimitedException("slow down"));

    RunDailySession.Result result = useCase.execute(cmd);

    assertThat(result.status()).isEqualTo("STOPPED_429");
    assertThat(result.remainingBudget()).isEqualTo(80);
    assertThat(result.refreshEnqueued()).isZero();
    assertThat(result.queueProcessed()).isZero();
    assertThat(result.message()).isEqualTo("slow down");
  }
}
