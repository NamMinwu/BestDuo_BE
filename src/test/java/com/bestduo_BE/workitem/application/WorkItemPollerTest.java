package com.bestduo_BE.workitem.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.workitem.domain.model.WorkItemType;
import com.bestduo_BE.workitem.infra.persistence.entity.WorkItem;
import com.bestduo_BE.workitem.infra.persistence.repository.WorkItemJpaRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkItemPollerTest {

  @Mock private WorkItemJpaRepository workItemJpaRepository;
  @Mock private WorkItemWorker workItemWorker;

  private WorkItemPoller poller;

  @BeforeEach
  void setUp() {
    poller = new WorkItemPoller(workItemJpaRepository, workItemWorker);
  }

  @Test
  void pollOncePicksReadyWorkItemAndDelegatesToWorker() {
    WorkItem item = WorkItem.ready(1L, "15.7", Tier.MASTER, WorkItemType.VERIFY_SUMMONERS, 1, 10);
    given(workItemJpaRepository.findAll()).willReturn(List.of(item));
    given(workItemWorker.execute(item.getId())).willReturn(new WorkItemWorker.WorkerResult(item.getId(), item.getType(), "DONE"));

    WorkItemWorker.WorkerResult result = poller.pollOnce();

    verify(workItemWorker).execute(item.getId());
    assertThat(result.status()).isEqualTo("DONE");
  }
}
