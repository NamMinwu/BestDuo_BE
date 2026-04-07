package com.bestduo_BE.coverage.application;

import com.bestduo_BE.config.WorkItemProperties;
import com.bestduo_BE.coverage.domain.model.CoverageBucketStatus;
import com.bestduo_BE.coverage.infra.persistence.entity.CoverageBucket;
import com.bestduo_BE.coverage.infra.persistence.repository.CoverageBucketCountJpaRepository;
import com.bestduo_BE.coverage.infra.persistence.repository.CoverageBucketJpaRepository;
import com.bestduo_BE.common.infra.persistence.repository.IngestQueueStatsJpaRepository;
import com.bestduo_BE.common.infra.persistence.repository.SummonerJpaRepository;
import com.bestduo_BE.workitem.application.port.WorkItemDispatcher;
import com.bestduo_BE.workitem.domain.model.WorkItemStatus;
import com.bestduo_BE.workitem.domain.model.WorkItemType;
import com.bestduo_BE.workitem.infra.persistence.entity.WorkItem;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CoverageScheduler {

  private final CoverageBucketJpaRepository coverageBucketRepository;
  private final CoverageBucketCountJpaRepository coverageBucketCountRepository;
  private final SummonerJpaRepository summonerJpaRepository;
  private final IngestQueueStatsJpaRepository ingestQueueStatsJpaRepository;
  private final WorkItemDispatcher workItemDispatcher;
  private final WorkItemProperties workItemProperties;

  @Scheduled(fixedDelayString = "${work-item.scheduler-fixed-delay-ms:30000}")
  public void scheduledRun() {
    schedule();
  }

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

      created.add(workItemDispatcher.emit(WorkItem.pending(
          bucket.getId(),
          bucket.getPatch(),
          bucket.getTier(),
          nextType,
          bucket.getPriority(),
          batchLimit(nextType),
          payloadFor(bucket, nextType)
      )));
    }

    return new ScheduleResult(created.size(), created);
  }

  private WorkItemType determineNextWorkItem(CoverageBucket bucket) {
    long verifiedPool = summonerJpaRepository.countByLastKnownTier(bucket.getTier());
    if (verifiedPool < workItemProperties.getThreshold().getVerifiedPool()) {
      return WorkItemType.VERIFY_SUMMONERS;
    }

    long queuedMatchIds = ingestQueueStatsJpaRepository.countReadyByTier(bucket.getTier().name());
    if (queuedMatchIds < workItemProperties.getThreshold().getReadyMatchQueue()) {
      return WorkItemType.REFRESH_SUMMONERS;
    }

    long unverifiedBacklog = summonerJpaRepository.countUnverifiedCandidates(bucket.getTier().name());
    if (unverifiedBacklog > 0) {
      return WorkItemType.VERIFY_SUMMONERS;
    }

    // recentIngested를 queuedMatchIds > 0 앞으로 fetch해 SEED 경로가 도달 가능하도록 수정
    long recentIngested = ingestQueueStatsJpaRepository.countDoneInLastMinutesByTier(
        workItemProperties.getThreshold().getIngestWindowMinutes(),
        bucket.getTier().name()
    );

    if (queuedMatchIds > 0 || recentIngested < workItemProperties.getThreshold().getRecentIngest()) {
      return WorkItemType.INGEST_MATCH_DETAIL;
    }

    if (verifiedPool < workItemProperties.getThreshold().getMinSeedTrigger()
        || unverifiedBacklog < workItemProperties.getThreshold().getUnverifiedBacklog()) {
      return WorkItemType.SEED_SUMMONERS;
    }

    return null;
  }

  private boolean hasPending(CoverageBucket bucket, WorkItemType type) {
    return workItemDispatcher.countByTypePatchTierStatuses(
        type,
        bucket.getPatch(),
        bucket.getTier(),
        List.of(WorkItemStatus.PENDING, WorkItemStatus.RUNNING)
    )
        >= workItemProperties.getDuplicatePendingLimit();
  }

  private int batchLimit(WorkItemType type) {
    return switch (type) {
      case INGEST_MATCH_DETAIL -> workItemProperties.getBatch().getIngest();
      case REFRESH_SUMMONERS -> workItemProperties.getBatch().getRefresh();
      case VERIFY_SUMMONERS -> workItemProperties.getBatch().getVerify();
      case SEED_SUMMONERS -> workItemProperties.getBatch().getSeed();
    };
  }

  private String payloadFor(CoverageBucket bucket, WorkItemType type) {
    if (type != WorkItemType.SEED_SUMMONERS) {
      return null;
    }
    return "{\"queue\":\"RANKED_SOLO_5x5\",\"division\":\"I\",\"page\":1,\"tier\":\"%s\"}"
        .formatted(bucket.getTier().name());
  }

  public record ScheduleResult(int createdCount, List<WorkItem> workItems) {
  }
}
