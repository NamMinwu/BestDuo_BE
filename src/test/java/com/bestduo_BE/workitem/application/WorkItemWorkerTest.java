package com.bestduo_BE.workitem.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.riot.budget.BudgetExhaustedException;
import com.bestduo_BE.common.infra.riot.exception.RiotRateLimitedException;
import com.bestduo_BE.workitem.application.port.WorkItemDispatcher;
import com.bestduo_BE.workitem.application.worker.WorkerContract;
import com.bestduo_BE.workitem.domain.model.WorkItemStatus;
import com.bestduo_BE.workitem.domain.model.WorkItemType;
import com.bestduo_BE.workitem.infra.persistence.entity.WorkItem;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkItemWorkerTest {

  @Mock private WorkItemDispatcher workItemDispatcher;
  @Mock private WorkerContract workerContract;

  private WorkItemWorker worker;

  @BeforeEach
  void setUp() {
    given(workerContract.type()).willReturn(WorkItemType.REFRESH_SUMMONERS);
    worker = new WorkItemWorker(workItemDispatcher, List.of(workerContract));
  }

  @Test
  void executeRefreshWorkItemMarksDone() {
    WorkItem item = WorkItem.pending(1L, "15.7", Tier.MASTER, WorkItemType.REFRESH_SUMMONERS, 1, 10, null);

    WorkItemWorker.WorkerResult result = worker.execute(item);

    verify(workerContract).execute(item);
    verify(workItemDispatcher).markDone(item.getId());
    assertThat(result.status()).isEqualTo(WorkItemStatus.DONE);
  }

  @Test
  void budgetExhaustedCallsMarkPendingAndPropagates() {
    WorkItem item = WorkItem.pending(1L, "15.7", Tier.MASTER, WorkItemType.REFRESH_SUMMONERS, 1, 10, null);
    willThrow(new BudgetExhaustedException("budget gone")).given(workerContract).execute(item);

    assertThatThrownBy(() -> worker.execute(item))
        .isInstanceOf(BudgetExhaustedException.class);

    verify(workItemDispatcher).markPending(item.getId());
    verifyNoMoreInteractions(workItemDispatcher);
  }

  @Test
  void rateLimitedCallsMarkPendingAndPropagates() {
    WorkItem item = WorkItem.pending(1L, "15.7", Tier.MASTER, WorkItemType.REFRESH_SUMMONERS, 1, 10, null);
    willThrow(new RiotRateLimitedException("429")).given(workerContract).execute(item);

    assertThatThrownBy(() -> worker.execute(item))
        .isInstanceOf(RiotRateLimitedException.class);

    verify(workItemDispatcher).markPending(item.getId());
    verifyNoMoreInteractions(workItemDispatcher);
  }

  @Test
  void generalExceptionCallsMarkError() {
    WorkItem item = WorkItem.pending(1L, "15.7", Tier.MASTER, WorkItemType.REFRESH_SUMMONERS, 1, 10, null);
    willThrow(new RuntimeException("unexpected")).given(workerContract).execute(item);

    WorkItemWorker.WorkerResult result = worker.execute(item);

    verify(workItemDispatcher).markError(item.getId(), "unexpected");
    assertThat(result.status()).isEqualTo(WorkItemStatus.ERROR);
  }
}
