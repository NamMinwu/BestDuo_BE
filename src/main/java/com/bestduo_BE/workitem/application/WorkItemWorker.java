package com.bestduo_BE.workitem.application;

import com.bestduo_BE.common.domain.model.SeedBootstrapCommand;
import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.riot.KeyLease;
import com.bestduo_BE.common.infra.riot.RiotKeyPool;
import com.bestduo_BE.coverage.infra.persistence.repository.CoverageBucketJpaRepository;
import com.bestduo_BE.ingest.application.MatchIngestWorker;
import com.bestduo_BE.refresh.application.RefreshBatchExecutor;
import com.bestduo_BE.seed.application.SeedBootstrapExecutor;
import com.bestduo_BE.workitem.domain.model.WorkItemType;
import com.bestduo_BE.workitem.infra.persistence.entity.WorkItem;
import com.bestduo_BE.workitem.infra.persistence.repository.WorkItemJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WorkItemWorker {

  private final WorkItemJpaRepository workItemJpaRepository;
  private final CoverageBucketJpaRepository coverageBucketJpaRepository;
  private final SeedBootstrapExecutor seedBootstrapExecutor;
  private final RefreshBatchExecutor refreshBatchExecutor;
  private final MatchIngestWorker matchIngestWorker;
  private final RiotKeyPool riotKeyPool;

  @Transactional
  public WorkerResult execute(Long workItemId) {
    WorkItem item = workItemJpaRepository.findById(workItemId).orElseThrow();
    item.markRunning();

    try (KeyLease ignored = riotKeyPool.leaseForWorker()) {
      switch (item.getType()) {
        case VERIFY_SUMMONERS, REFRESH_SUMMONERS -> refreshBatchExecutor.execute(item.getBatchLimit(), item.getTier());
        case INGEST_MATCH_DETAIL -> matchIngestWorker.execute(item.getBatchLimit(), item.getTier());
        case SEED_SUMMONERS -> seedBootstrapExecutor.execute(new SeedBootstrapCommand(
            "RANKED_SOLO_5x5",
            item.getTier().name(),
            "I",
            item.getTier(),
            1,
            1,
            20,
            item.getBatchLimit() == null ? 0 : item.getBatchLimit()
        ));
      }
      item.markDone();
      return new WorkerResult(workItemId, item.getType(), "DONE");
    } catch (Exception e) {
      item.markError();
      return new WorkerResult(workItemId, item.getType(), "ERROR");
    } finally {
      riotKeyPool.clearWorkerLease();
    }
  }

  public record WorkerResult(Long workItemId, WorkItemType type, String status) {
  }
}
