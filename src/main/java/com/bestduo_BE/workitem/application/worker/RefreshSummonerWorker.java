package com.bestduo_BE.workitem.application.worker;

import com.bestduo_BE.common.infra.riot.KeyLease;
import com.bestduo_BE.refresh.application.RefreshBatchExecutor;
import com.bestduo_BE.workitem.domain.model.WorkItemType;
import com.bestduo_BE.workitem.infra.persistence.entity.WorkItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RefreshSummonerWorker implements WorkerContract {

  private final RefreshBatchExecutor refreshBatchExecutor;

  @Override
  public WorkItemType type() {
    return WorkItemType.REFRESH_SUMMONERS;
  }

  @Override
  public void execute(WorkItem workItem, KeyLease keyLease) {
    refreshBatchExecutor.execute(workItem.getBatchLimit(), workItem.getTier());
  }
}
