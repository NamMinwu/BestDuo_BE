package com.bestduo_BE.workitem.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.riot.KeyLease;
import com.bestduo_BE.common.infra.riot.RiotKeyPool;
import com.bestduo_BE.ingest.application.MatchIngestWorker;
import com.bestduo_BE.refresh.application.RefreshBatchExecutor;
import com.bestduo_BE.seed.application.SeedBootstrapExecutor;
import com.bestduo_BE.workitem.domain.model.WorkItemStatus;
import com.bestduo_BE.workitem.domain.model.WorkItemType;
import com.bestduo_BE.workitem.infra.persistence.entity.WorkItem;
import com.bestduo_BE.workitem.infra.persistence.repository.WorkItemJpaRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkItemWorkerTest {

  @Mock private WorkItemJpaRepository workItemJpaRepository;
  @Mock private SeedBootstrapExecutor seedBootstrapExecutor;
  @Mock private RefreshBatchExecutor refreshBatchExecutor;
  @Mock private MatchIngestWorker matchIngestWorker;
  @Mock private RiotKeyPool riotKeyPool;
  @Mock private KeyLease keyLease;

  private WorkItemWorker worker;

  @BeforeEach
  void setUp() {
    worker = new WorkItemWorker(
        workItemJpaRepository,
        seedBootstrapExecutor,
        refreshBatchExecutor,
        matchIngestWorker,
        riotKeyPool
    );
  }

  @Test
  void executeRefreshWorkItemUsesWorkerLeaseAndMarksDone() {
    WorkItem item = WorkItem.ready(1L, "15.7", Tier.MASTER, WorkItemType.REFRESH_SUMMONERS, 1, 10);
    given(workItemJpaRepository.findById(1L)).willReturn(Optional.of(item));
    given(riotKeyPool.leaseForWorker()).willReturn(keyLease);
    given(refreshBatchExecutor.execute(10, Tier.MASTER)).willReturn(new RefreshBatchExecutor.Result(1, 1, 0, 3));

    WorkItemWorker.WorkerResult result = worker.execute(1L);

    verify(refreshBatchExecutor).execute(10, Tier.MASTER);
    verify(riotKeyPool).clearWorkerLease();
    assertThat(result.status()).isEqualTo(WorkItemStatus.DONE);
    assertThat(item.getStatus()).isEqualTo(WorkItemStatus.DONE);
  }
}
