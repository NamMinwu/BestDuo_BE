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

      String payload = payloadFor(bucket, nextType);
      created.add(workItemDispatcher.emit(WorkItem.pending(
          bucket.getId(),
          bucket.getPatch(),
          bucket.getTier(),
          nextType,
          bucket.getPriority(),
          batchLimit(nextType),
          payload
      )));
      // SEED 발행 직후 다음 실행을 위해 페이지/division 전진
      // @Transactional 범위 내이므로 emit 실패 시 함께 롤백됨
      if (nextType == WorkItemType.SEED_SUMMONERS) {
        bucket.advanceSeedState(workItemProperties.getBatch().getSeedMaxPagesPerDivision());
      }
    }

    return new ScheduleResult(created.size(), created);
  }

  private WorkItemType determineNextWorkItem(CoverageBucket bucket) {
    long verifiedPool = summonerJpaRepository.countByLastKnownTier(bucket.getTier());

    // verified pool 부족 → SEED로 소환사 추가 (SEED 시점에 tier가 즉시 저장됨)
    if (verifiedPool < workItemProperties.getThreshold().getVerifiedPool()) {
      return WorkItemType.SEED_SUMMONERS;
    }

    long queuedMatchIds = ingestQueueStatsJpaRepository.countReadyByTier(bucket.getTier().name());
    if (queuedMatchIds < workItemProperties.getThreshold().getReadyMatchQueue()) {
      return WorkItemType.REFRESH_SUMMONERS;
    }

    long recentIngested = ingestQueueStatsJpaRepository.countDoneInLastMinutesByTier(
        workItemProperties.getThreshold().getIngestWindowMinutes(),
        bucket.getTier().name()
    );

    if (queuedMatchIds > 0 || recentIngested < workItemProperties.getThreshold().getRecentIngest()) {
      return WorkItemType.INGEST_MATCH_DETAIL;
    }

    if (verifiedPool < workItemProperties.getThreshold().getMinSeedTrigger()) {
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
      case SEED_SUMMONERS -> workItemProperties.getBatch().getSeed();
    };
  }

  private String payloadFor(CoverageBucket bucket, WorkItemType type) {
    if (type != WorkItemType.SEED_SUMMONERS) {
      return null;
    }
    // seedPage/seedDivision은 버킷에 저장된 현재 진행 상태를 사용한다.
    // 이 메서드 호출 이후 advanceSeedState()가 상태를 전진시키므로,
    // 다음 SEED WorkItem은 다른 페이지/division을 가리키게 된다.
    return "{\"queue\":\"RANKED_SOLO_5x5\",\"division\":\"%s\",\"page\":%d,\"tier\":\"%s\"}"
        .formatted(bucket.getSeedDivision(), bucket.getSeedPage(), bucket.getTier().name());
  }

  public record ScheduleResult(int createdCount, List<WorkItem> workItems) {
  }
}
