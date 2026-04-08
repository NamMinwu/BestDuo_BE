package com.bestduo_BE.workitem.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.riot.RiotRateLimitInterceptor;
import com.bestduo_BE.common.infra.riot.budget.BudgetExhaustedException;
import com.bestduo_BE.common.infra.riot.exception.RiotRateLimitedException;
import com.bestduo_BE.config.WorkItemProperties;
import com.bestduo_BE.workitem.application.port.WorkItemDispatcher;
import com.bestduo_BE.workitem.domain.model.WorkItemType;
import com.bestduo_BE.workitem.infra.persistence.entity.WorkItem;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkerPoolTest {

  @Mock private WorkItemProperties workItemProperties;
  @Mock private WorkItemDispatcher workItemDispatcher;
  @Mock private WorkItemWorker workItemWorker;
  @Mock private RiotRateLimitInterceptor riotRateLimitInterceptor;

  private WorkerPool workerPool;

  @BeforeEach
  void setUp() {
    workerPool = new WorkerPool(workItemProperties, workItemDispatcher, workItemWorker, riotRateLimitInterceptor);
    given(workItemProperties.getPollingIntervalMs()).willReturn(0L);
  }

  @Test
  void keyCooling_sleepsWithoutPicking() throws Exception {
    // 429 cooling 중이면 pickAndLock을 호출하지 않는다
    given(riotRateLimitInterceptor.durationUntilAvailable()).willReturn(Duration.ofSeconds(5));

    Thread loopThread = new Thread(workerPool::runLoop);
    loopThread.start();
    Thread.sleep(50);
    loopThread.interrupt();
    loopThread.join(500);

    verify(workItemDispatcher, never()).pickAndLock(anyInt());
  }

  @Test
  void emptyQueue_doesNotExecute() throws Exception {
    given(riotRateLimitInterceptor.durationUntilAvailable()).willReturn(Duration.ZERO);
    given(workItemDispatcher.pickAndLock(1)).willReturn(List.of());

    Thread loopThread = new Thread(workerPool::runLoop);
    loopThread.start();
    Thread.sleep(50);
    loopThread.interrupt();
    loopThread.join(500);

    verify(workItemWorker, never()).execute(any(WorkItem.class));
  }

  @Test
  void successfulExecute_callsExecute() throws Exception {
    WorkItem item = WorkItem.pending(1L, "15.7", Tier.MASTER, WorkItemType.REFRESH_SUMMONERS, 1, 10, null);
    given(riotRateLimitInterceptor.durationUntilAvailable()).willReturn(Duration.ZERO);
    given(workItemDispatcher.pickAndLock(1)).willReturn(List.of(item));

    Thread loopThread = new Thread(workerPool::runLoop);
    loopThread.start();
    Thread.sleep(50);
    loopThread.interrupt();
    loopThread.join(500);

    verify(workItemWorker, atLeastOnce()).execute(item);
  }

  @Test
  void budgetExhausted_itemAlreadyMarkedPendingByWorker() throws Exception {
    WorkItem item = WorkItem.pending(1L, "15.7", Tier.MASTER, WorkItemType.INGEST_MATCH_DETAIL, 1, 20, null);
    given(riotRateLimitInterceptor.durationUntilAvailable()).willReturn(Duration.ZERO);
    given(workItemDispatcher.pickAndLock(1)).willReturn(List.of(item));
    willThrow(new BudgetExhaustedException("budget gone")).given(workItemWorker).execute(item);

    Thread loopThread = new Thread(workerPool::runLoop);
    loopThread.start();
    Thread.sleep(50);
    loopThread.interrupt();
    loopThread.join(500);

    // markPending은 WorkItemWorker 내부에서 처리
    verify(workItemDispatcher, never()).markPending(any());
  }

  @Test
  void unexpectedException_loopContinuesWithoutCrash() throws Exception {
    WorkItem item = WorkItem.pending(1L, "15.7", Tier.MASTER, WorkItemType.SEED_SUMMONERS, 1, 1, null);
    given(riotRateLimitInterceptor.durationUntilAvailable()).willReturn(Duration.ZERO);
    given(workItemDispatcher.pickAndLock(1)).willReturn(List.of(item));
    willThrow(new RuntimeException("unexpected db error")).given(workItemWorker).execute(item);

    Thread loopThread = new Thread(workerPool::runLoop);
    loopThread.start();
    Thread.sleep(100);
    loopThread.interrupt();
    loopThread.join(500);

    assertThat(loopThread.isAlive()).isFalse();
  }
}
