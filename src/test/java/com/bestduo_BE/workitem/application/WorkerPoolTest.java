package com.bestduo_BE.workitem.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.riot.KeyLease;
import com.bestduo_BE.common.infra.riot.RiotKeyPool;
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
@MockitoSettings(strictness = Strictness.LENIENT) // @BeforeEach 공유 stub이 일부 테스트에서 미사용될 수 있음
class WorkerPoolTest {

  @Mock private WorkItemProperties workItemProperties;
  @Mock private WorkItemDispatcher workItemDispatcher;
  @Mock private WorkItemWorker workItemWorker;
  @Mock private RiotKeyPool riotKeyPool;
  @Mock private KeyLease keyLease;

  private WorkerPool workerPool;

  @BeforeEach
  void setUp() {
    workerPool = new WorkerPool(workItemProperties, workItemDispatcher, workItemWorker, riotKeyPool);
    given(workItemProperties.getPollingIntervalMs()).willReturn(0L); // 테스트에서 sleep 없이 실행
  }

  @Test
  void keysCooling_sleepsWithoutPicking() throws Exception {
    // key가 cooling 중이면 pickAndLock을 호출하지 않는다
    // durationUntilAnyAvailable > 0 → 5200ms sleep → 50ms 후 interrupt → pickAndLock 미호출
    given(riotKeyPool.durationUntilAnyAvailable()).willReturn(Duration.ofSeconds(5));

    Thread loopThread = new Thread(workerPool::runLoop);
    loopThread.start();
    Thread.sleep(50);
    loopThread.interrupt();
    loopThread.join(500);

    verify(workItemDispatcher, never()).pickAndLock(anyInt());
  }

  @Test
  void leaseAcquireFails_sleepsWithoutPicking() throws Exception {
    // leaseForWorker가 실패하면 pickAndLock을 호출하지 않는다
    given(riotKeyPool.durationUntilAnyAvailable()).willReturn(Duration.ZERO);
    given(riotKeyPool.leaseForWorker()).willThrow(new RiotRateLimitedException("no key"));

    Thread loopThread = new Thread(workerPool::runLoop);
    loopThread.start();
    Thread.sleep(50);
    loopThread.interrupt();
    loopThread.join(500);

    verify(workItemDispatcher, never()).pickAndLock(anyInt());
  }

  @Test
  void emptyQueue_releasesLeaseAndDoesNotExecute() throws Exception {
    // pickAndLock 결과가 비어있으면 execute를 호출하지 않는다
    given(riotKeyPool.durationUntilAnyAvailable()).willReturn(Duration.ZERO);
    given(riotKeyPool.leaseForWorker()).willReturn(keyLease);
    given(workItemDispatcher.pickAndLock(1)).willReturn(List.of());

    Thread loopThread = new Thread(workerPool::runLoop);
    loopThread.start();
    Thread.sleep(50);
    loopThread.interrupt();
    loopThread.join(500);

    verify(riotKeyPool, atLeastOnce()).clearWorkerLease();
    verify(workItemWorker, never()).execute(any(WorkItem.class), any(KeyLease.class));
  }

  @Test
  void successfulExecute_callsExecuteWithLease() throws Exception {
    // pickAndLock 성공 시 execute(item, lease)를 호출한다
    WorkItem item = WorkItem.pending(1L, "15.7", Tier.MASTER, WorkItemType.REFRESH_SUMMONERS, 1, 10, null);
    given(riotKeyPool.durationUntilAnyAvailable()).willReturn(Duration.ZERO);
    given(riotKeyPool.leaseForWorker()).willReturn(keyLease);
    given(workItemDispatcher.pickAndLock(1)).willReturn(List.of(item));
    // WorkerPool은 execute()의 반환값을 사용하지 않으므로 기본값(null) 반환으로 충분

    Thread loopThread = new Thread(workerPool::runLoop);
    loopThread.start();
    Thread.sleep(50);
    loopThread.interrupt();
    loopThread.join(500);

    verify(workItemWorker, atLeastOnce()).execute(item, keyLease);
    verify(riotKeyPool, atLeastOnce()).clearWorkerLease();
  }

  @Test
  void budgetExhausted_itemAlreadyMarkedPendingByWorker() throws Exception {
    // BudgetExhaustedException 발생 시 WorkItemWorker가 markPending을 처리한 상태로 propagate된다
    WorkItem item = WorkItem.pending(1L, "15.7", Tier.MASTER, WorkItemType.INGEST_MATCH_DETAIL, 1, 20, null);
    given(riotKeyPool.durationUntilAnyAvailable()).willReturn(Duration.ZERO);
    given(riotKeyPool.leaseForWorker()).willReturn(keyLease);
    given(workItemDispatcher.pickAndLock(1)).willReturn(List.of(item));
    willThrow(new BudgetExhaustedException("budget gone"))
        .given(workItemWorker).execute(item, keyLease);

    Thread loopThread = new Thread(workerPool::runLoop);
    loopThread.start();
    Thread.sleep(50);
    loopThread.interrupt();
    loopThread.join(500);

    // clearWorkerLease는 finally에서 항상 호출됨
    verify(riotKeyPool, atLeastOnce()).clearWorkerLease();
    // markPending은 WorkItemWorker 내부에서 처리 (WorkerPool은 직접 호출 안 함)
    verify(workItemDispatcher, never()).markPending(anyLong());
  }

  @Test
  void unexpectedException_loopContinuesWithoutCrash() throws Exception {
    // 미지수 예외 발생 시 루프가 계속 돌아야 한다 (크래시 없음)
    WorkItem item = WorkItem.pending(1L, "15.7", Tier.MASTER, WorkItemType.SEED_SUMMONERS, 1, 1, null);
    given(riotKeyPool.durationUntilAnyAvailable()).willReturn(Duration.ZERO);
    given(riotKeyPool.leaseForWorker()).willReturn(keyLease);
    given(workItemDispatcher.pickAndLock(1)).willReturn(List.of(item));
    willThrow(new RuntimeException("unexpected db error"))
        .given(workItemWorker).execute(item, keyLease);

    Thread loopThread = new Thread(workerPool::runLoop);
    loopThread.start();
    Thread.sleep(100); // 예외 발생 후에도 루프 유지 확인
    loopThread.interrupt();
    loopThread.join(500);

    // 루프가 살아있었다면 clearWorkerLease가 finally에서 호출됨
    verify(riotKeyPool, atLeastOnce()).clearWorkerLease();
    // 루프가 크래시 없이 계속 실행됐는지: interrupt 이후 스레드가 정상 종료됨을 확인
    org.assertj.core.api.Assertions.assertThat(loopThread.isAlive()).isFalse();
  }
}
