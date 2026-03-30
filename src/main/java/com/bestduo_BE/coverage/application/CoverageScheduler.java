package com.bestduo_BE.coverage.application;

import com.bestduo_BE.coverage.domain.model.CoverageBucketStatus;
import com.bestduo_BE.coverage.infra.persistence.entity.CoverageBucket;
import com.bestduo_BE.coverage.infra.persistence.repository.CoverageBucketCountJpaRepository;
import com.bestduo_BE.coverage.infra.persistence.repository.CoverageBucketJpaRepository;
import com.bestduo_BE.common.infra.persistence.repository.IngestQueueStatsJpaRepository;
import com.bestduo_BE.common.infra.persistence.repository.SummonerJpaRepository;
import com.bestduo_BE.workitem.domain.model.WorkItemStatus;
import com.bestduo_BE.workitem.domain.model.WorkItemType;
import com.bestduo_BE.workitem.infra.persistence.entity.WorkItem;
import com.bestduo_BE.workitem.infra.persistence.repository.WorkItemJpaRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CoverageScheduler {

  private static final long MIN_VERIFIED_POOL = 10L;
  private static final long MIN_UNVERIFIED_BACKLOG = 5L;
  private static final long MIN_READY_MATCH_QUEUE = 20L;
  private static final long MIN_RECENT_INGEST = 5L;

  private final CoverageBucketJpaRepository coverageBucketRepository;
  private final CoverageBucketCountJpaRepository coverageBucketCountRepository;
  private final SummonerJpaRepository summonerJpaRepository;
  private final IngestQueueStatsJpaRepository ingestQueueStatsJpaRepository;
  private final WorkItemJpaRepository workItemJpaRepository;

  @Transactional
  public ScheduleResult schedule() {
    List<WorkItem> created = new ArrayList<>();

    for (CoverageBucket bucket : coverageBucketRepository.findAllByOrderByPriorityAscIdAsc()) {
      if (bucket.getStatus() != CoverageBucketStatus.COLLECTING) {
        continue;
      }

      bucket.refreshCount(coverageBucketCountRepository.countDistinctMatches(bucket.getPatch(), bucket.getTier().name()));
      if (bucket.getStatus() != CoverageBucketStatus.COLLECTING) {
        continue;
      }

      WorkItemType nextType = determineNextWorkItem(bucket);
      if (nextType == null || hasPending(bucket, nextType)) {
        continue;
      }

      created.add(workItemJpaRepository.save(WorkItem.ready(
          bucket.getId(),
          bucket.getPatch(),
          bucket.getTier(),
          nextType,
          bucket.getPriority(),
          switch (nextType) {
            case INGEST_MATCH_DETAIL -> 20;
            case REFRESH_SUMMONERS, VERIFY_SUMMONERS -> 10;
            case SEED_SUMMONERS -> 1;
          }
      )));
    }

    return new ScheduleResult(created.size(), created);
  }

  private WorkItemType determineNextWorkItem(CoverageBucket bucket) {
    long verifiedPool = summonerJpaRepository.countByLastKnownTier(bucket.getTier());
    if (verifiedPool < MIN_VERIFIED_POOL) {
      return WorkItemType.VERIFY_SUMMONERS;
    }

    long queuedMatchIds = ingestQueueStatsJpaRepository.countReadyByTier(bucket.getTier().name());
    if (queuedMatchIds < MIN_READY_MATCH_QUEUE) {
      return WorkItemType.REFRESH_SUMMONERS;
    }

    long recentIngested = ingestQueueStatsJpaRepository.countDoneInLastMinutesByTier(30, bucket.getTier().name());
    if (recentIngested < MIN_RECENT_INGEST) {
      return WorkItemType.INGEST_MATCH_DETAIL;
    }

    long unverifiedBacklog = summonerJpaRepository.countUnverifiedCandidates(bucket.getTier().name());
    if (unverifiedBacklog < MIN_UNVERIFIED_BACKLOG) {
      return WorkItemType.SEED_SUMMONERS;
    }

    return null;
  }

  private boolean hasPending(CoverageBucket bucket, WorkItemType type) {
    return workItemJpaRepository.existsByCoverageBucketIdAndTypeAndStatusIn(
        bucket.getId(),
        type,
        List.of(WorkItemStatus.READY, WorkItemStatus.RUNNING)
    );
  }

  public record ScheduleResult(int createdCount, List<WorkItem> workItems) {
  }
}
