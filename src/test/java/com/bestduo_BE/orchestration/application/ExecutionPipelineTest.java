package com.bestduo_BE.orchestration.application;

import com.bestduo_BE.ingest.application.MatchIngestWorker;
import com.bestduo_BE.refresh.application.RefreshBatchExecutor;
import com.bestduo_BE.seed.application.SeedBootstrapExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.bestduo_BE.common.domain.model.SeedBootstrapCommand;
import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.riot.budget.BudgetExhaustedException;
import com.bestduo_BE.common.infra.riot.budget.RiotRequestBudget;
import com.bestduo_BE.common.infra.riot.exception.RiotRateLimitedException;
import com.bestduo_BE.monitoring.QueryCountMonitor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExecutionPipelineTest {

  @Mock
  private SeedBootstrapExecutor seedBootstrapExecutor;

  @Mock
  private RefreshBatchExecutor refreshBatchExecutor;

  @Mock
  private MatchIngestWorker matchIngestWorker;

  private ExecutionPipeline useCase;
  private QueryCountMonitor queryCountMonitor;

  private final SeedBootstrapCommand seedCommand = new SeedBootstrapCommand(
      "RANKED_SOLO", "GOLD", "I", Tier.GOLD, 1, 1, 20, 0
  );

  @BeforeEach
  void setUp() {
    queryCountMonitor = new QueryCountMonitor();
    useCase = new ExecutionPipeline(seedBootstrapExecutor, refreshBatchExecutor, matchIngestWorker, queryCountMonitor);
  }

  @AfterEach
  void tearDown() {
    RiotRequestBudget.clear();
  }

  @Test
  void executeRunsEnabledPhasesAndAggregatesCounts() {
    ExecutionPipeline.ExecutionCommand cmd = new ExecutionPipeline.ExecutionCommand(
        20, 20, 80, true, true, 5, 10, 3, seedCommand
    );
    given(seedBootstrapExecutor.execute(seedCommand))
        .willReturn(new SeedBootstrapExecutor.SeedBootstrapResult(1, 2, 3, 4, 5));
    given(refreshBatchExecutor.execute(5))
        .willReturn(new RefreshBatchExecutor.Result(2, 2, 0, 7));
    given(matchIngestWorker.execute(10))
        .willReturn(
            new MatchIngestWorker.Result(0, 2, 5, 5, 0, 5),
            new MatchIngestWorker.Result(0, 0, 0, 0, 0, 0)
        );

    ExecutionPipeline.Result result = useCase.execute(cmd);

    verify(seedBootstrapExecutor).execute(seedCommand);
    verify(refreshBatchExecutor).execute(5);
    verify(matchIngestWorker, times(2)).execute(10);
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
    ExecutionPipeline.ExecutionCommand cmd = new ExecutionPipeline.ExecutionCommand(
        0, 0, 50, false, false, 0, 10, 1, seedCommand
    );
    given(matchIngestWorker.execute(10))
        .willThrow(new BudgetExhaustedException("budget gone"));

    ExecutionPipeline.Result result = useCase.execute(cmd);

    verify(seedBootstrapExecutor, never()).execute(seedCommand);
    verify(refreshBatchExecutor, never()).execute(0);
    assertThat(result.status()).isEqualTo("STOPPED_BUDGET");
    assertThat(result.remainingBudget()).isZero();
    assertThat(result.refreshEnqueued()).isZero();
    assertThat(result.queueProcessed()).isZero();
    assertThat(result.message()).isEqualTo("budget gone");
  }

  @Test
  void executeStopsWhenRateLimited() {
    ExecutionPipeline.ExecutionCommand cmd = new ExecutionPipeline.ExecutionCommand(
        0, 0, 80, false, false, 0, 10, 1, seedCommand
    );
    given(matchIngestWorker.execute(10))
        .willThrow(new RiotRateLimitedException("slow down"));

    ExecutionPipeline.Result result = useCase.execute(cmd);

    assertThat(result.status()).isEqualTo("STOPPED_429");
    assertThat(result.remainingBudget()).isEqualTo(80);
    assertThat(result.refreshEnqueued()).isZero();
    assertThat(result.queueProcessed()).isZero();
    assertThat(result.message()).isEqualTo("slow down");
  }
}
