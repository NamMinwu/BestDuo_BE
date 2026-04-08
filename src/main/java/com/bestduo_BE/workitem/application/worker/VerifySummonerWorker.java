package com.bestduo_BE.workitem.application.worker;

import com.bestduo_BE.refresh.application.TierVerifyBatchExecutor;
import com.bestduo_BE.workitem.domain.model.WorkItemType;
import com.bestduo_BE.workitem.infra.persistence.entity.WorkItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VerifySummonerWorker implements WorkerContract {

  private final TierVerifyBatchExecutor tierVerifyBatchExecutor;

  @Override
  public WorkItemType type() {
    return WorkItemType.VERIFY_SUMMONERS;
  }

  @Override
  public void execute(WorkItem workItem) {
    tierVerifyBatchExecutor.execute(workItem.getBatchLimit(), workItem.getTier());
  }
}
