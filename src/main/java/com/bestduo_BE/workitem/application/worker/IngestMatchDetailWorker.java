package com.bestduo_BE.workitem.application.worker;

import com.bestduo_BE.ingest.application.MatchIngestWorker;
import com.bestduo_BE.workitem.domain.model.WorkItemType;
import com.bestduo_BE.workitem.infra.persistence.entity.WorkItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class IngestMatchDetailWorker implements WorkerContract {

  private final MatchIngestWorker matchIngestWorker;

  @Override
  public WorkItemType type() {
    return WorkItemType.INGEST_MATCH_DETAIL;
  }

  @Override
  public void execute(WorkItem workItem) {
    matchIngestWorker.execute(workItem.getBatchLimit(), workItem.getTier(), workItem.getPatch());
  }
}
