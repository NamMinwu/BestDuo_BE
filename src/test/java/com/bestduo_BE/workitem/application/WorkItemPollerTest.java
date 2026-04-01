package com.bestduo_BE.workitem.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.workitem.application.port.WorkItemDispatcher;
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
class WorkItemPollerTest {

  @Mock private WorkItemDispatcher workItemDispatcher;
  @Mock private WorkItemWorker workItemWorker;

  private WorkItemPoller poller;

  @BeforeEach
  void setUp() {
    poller = new WorkItemPoller(workItemDispatcher, workItemWorker);
  }

  @Test
  void pollOncePicksPendingWorkItemAndDelegatesToWorker() {
    WorkItem item = WorkItem.pending(1L, "15.7", Tier.MASTER, WorkItemType.VERIFY_SUMMONERS, 1, 10, null);
    item.markRunning();
    given(workItemDispatcher.pickAndLock(1)).willReturn(List.of(item));
    given(workItemWorker.execute(item)).willReturn(new WorkItemWorker.WorkerResult(item.getId(), item.getType(), WorkItemStatus.DONE));

    WorkItemWorker.WorkerResult result = poller.pollOnce();

    verify(workItemWorker).execute(item);
    assertThat(result.status()).isEqualTo(WorkItemStatus.DONE);
  }
}
