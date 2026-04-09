package com.bestduo_BE.workitem.infra.persistence;

import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.workitem.application.port.WorkItemDispatcher;
import com.bestduo_BE.workitem.domain.model.WorkItemStatus;
import com.bestduo_BE.workitem.domain.model.WorkItemType;
import com.bestduo_BE.workitem.infra.persistence.entity.WorkItem;
import com.bestduo_BE.workitem.infra.persistence.repository.WorkItemJpaRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class WorkItemDispatcherImpl implements WorkItemDispatcher {

  private final WorkItemJpaRepository repository;

  @Override
  @Transactional
  public WorkItem emit(WorkItem workItem) {
    return repository.save(workItem);
  }

  @Override
  @Transactional
  public List<WorkItem> pickAndLock(int limit) {
    List<WorkItem> items = repository.pickPendingForUpdate(limit);
    items.forEach(WorkItem::markRunning);
    return items;
  }

  @Override
  @Transactional
  public int recoverStaleRunning(int staleMinutes) {
    return repository.recoverStaleRunning(staleMinutes);
  }

  @Override
  @Transactional
  public void markDone(Long workItemId) {
    WorkItem item = repository.findById(workItemId)
        .orElseThrow(() -> new IllegalStateException("WorkItem not found: id=" + workItemId));
    item.markDone();
  }

  @Override
  @Transactional
  public void markError(Long workItemId, String message) {
    WorkItem item = repository.findById(workItemId)
        .orElseThrow(() -> new IllegalStateException("WorkItem not found: id=" + workItemId));
    item.markError(message);
  }

  @Override
  @Transactional
  public void markPending(Long workItemId) {
    WorkItem item = repository.findById(workItemId)
        .orElseThrow(() -> new IllegalStateException("WorkItem not found: id=" + workItemId));
    item.markPending();
  }

  @Override
  public long countByTypePatchTierStatus(WorkItemType type, String patch, Tier tier, WorkItemStatus status) {
    return repository.countByTypeAndPatchAndTierAndStatus(type, patch, tier, status);
  }

  @Override
  public long countByTypePatchTierStatuses(WorkItemType type, String patch, Tier tier, List<WorkItemStatus> statuses) {
    return repository.countByTypeAndPatchAndTierAndStatusIn(type, patch, tier, statuses);
  }
}
