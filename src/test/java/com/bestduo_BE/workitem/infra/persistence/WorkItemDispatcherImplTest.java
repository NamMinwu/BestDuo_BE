package com.bestduo_BE.workitem.infra.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.workitem.domain.model.WorkItemStatus;
import com.bestduo_BE.workitem.domain.model.WorkItemType;
import com.bestduo_BE.workitem.infra.persistence.entity.WorkItem;
import com.bestduo_BE.workitem.infra.persistence.repository.WorkItemJpaRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkItemDispatcherImplTest {

  @Mock private WorkItemJpaRepository repository;

  private WorkItemDispatcherImpl dispatcher;

  @BeforeEach
  void setUp() {
    dispatcher = new WorkItemDispatcherImpl(repository);
  }

  @Test
  void pickAndLockMarksPendingItemsRunning() {
    WorkItem item = WorkItem.pending(1L, "15.7", Tier.MASTER, WorkItemType.VERIFY_SUMMONERS, 1, 10, null);
    given(repository.pickPendingForUpdate(1)).willReturn(List.of(item));

    List<WorkItem> picked = dispatcher.pickAndLock(1);

    assertThat(picked).singleElement().extracting(WorkItem::getStatus).isEqualTo(WorkItemStatus.RUNNING);
  }

  @Test
  void markDoneTransitionsItemToDone() {
    WorkItem item = WorkItem.pending(1L, "15.7", Tier.MASTER, WorkItemType.VERIFY_SUMMONERS, 1, 10, null);
    item.markRunning();
    given(repository.findById(1L)).willReturn(Optional.of(item));

    dispatcher.markDone(1L);

    assertThat(item.getStatus()).isEqualTo(WorkItemStatus.DONE);
  }

  @Test
  void markErrorTransitionsItemToErrorAndStoresMessage() {
    WorkItem item = WorkItem.pending(1L, "15.7", Tier.MASTER, WorkItemType.VERIFY_SUMMONERS, 1, 10, null);
    item.markRunning();
    given(repository.findById(1L)).willReturn(Optional.of(item));

    dispatcher.markError(1L, "boom");

    assertThat(item.getStatus()).isEqualTo(WorkItemStatus.ERROR);
    assertThat(item.getLastError()).isEqualTo("boom");
    verify(repository).findById(1L);
  }

  @Test
  void markPendingTransitionsRunningItemBackToPending() {
    WorkItem item = WorkItem.pending(1L, "15.7", Tier.MASTER, WorkItemType.INGEST_MATCH_DETAIL, 1, 10, null);
    item.markRunning();
    given(repository.findById(1L)).willReturn(Optional.of(item));

    dispatcher.markPending(1L);

    assertThat(item.getStatus()).isEqualTo(WorkItemStatus.PENDING);
    assertThat(item.getLockedAt()).isNull();
  }
}
