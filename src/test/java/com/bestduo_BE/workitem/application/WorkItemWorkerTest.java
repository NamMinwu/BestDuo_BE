package com.bestduo_BE.workitem.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.riot.KeyLease;
import com.bestduo_BE.common.infra.riot.RiotKeyPool;
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
  @Mock private RiotKeyPool riotKeyPool;
  @Mock private KeyLease keyLease;
  @Mock private WorkerContract workerContract;

  private WorkItemWorker worker;

  @BeforeEach
  void setUp() {
    given(workerContract.type()).willReturn(WorkItemType.REFRESH_SUMMONERS);
    worker = new WorkItemWorker(workItemDispatcher, riotKeyPool, List.of(workerContract));
  }

  @Test
  void executeRefreshWorkItemUsesWorkerLeaseAndMarksDone() {
    WorkItem item = WorkItem.pending(1L, "15.7", Tier.MASTER, WorkItemType.REFRESH_SUMMONERS, 1, 10, null);
    given(riotKeyPool.leaseForWorker()).willReturn(keyLease);

    WorkItemWorker.WorkerResult result = worker.execute(item);

    verify(workerContract).execute(item, keyLease);
    verify(workItemDispatcher).markDone(item.getId());
    verify(riotKeyPool).clearWorkerLease();
    assertThat(result.status()).isEqualTo(WorkItemStatus.DONE);
  }
}
