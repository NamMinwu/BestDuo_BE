package com.bestduo_BE.workitem.application.worker;

import com.bestduo_BE.common.infra.riot.KeyLease;
import com.bestduo_BE.refresh.application.RefreshBatchExecutor;
import com.bestduo_BE.workitem.domain.model.WorkItemType;
import com.bestduo_BE.workitem.infra.persistence.entity.WorkItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VerifySummonerWorker implements WorkerContract {

  private final RefreshBatchExecutor refreshBatchExecutor;

  @Override
  public WorkItemType type() {
    return WorkItemType.VERIFY_SUMMONERS;
  }

  @Override
  public void execute(WorkItem workItem, KeyLease keyLease) {
    // RefreshBatchExecutor는 findRefreshTargets를 tier_observed_at IS NULL 우선으로 정렬하므로,
    // 미검증 소환사(tier_observed_at = null)가 자동으로 먼저 처리된다.
    // tier 갱신 전용 executor가 별도로 생기면 그쪽으로 교체 예정.
    refreshBatchExecutor.execute(workItem.getBatchLimit(), workItem.getTier());
  }
}
