package com.bestduo_BE.coverage.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.persistence.repository.IngestQueueStatsJpaRepository;
import com.bestduo_BE.common.infra.persistence.repository.SummonerJpaRepository;
import com.bestduo_BE.coverage.infra.persistence.entity.CoverageBucket;
import com.bestduo_BE.coverage.infra.persistence.repository.CoverageBucketCountJpaRepository;
import com.bestduo_BE.coverage.infra.persistence.repository.CoverageBucketJpaRepository;
import com.bestduo_BE.workitem.domain.model.WorkItemStatus;
import com.bestduo_BE.workitem.domain.model.WorkItemType;
import com.bestduo_BE.workitem.infra.persistence.entity.WorkItem;
import com.bestduo_BE.workitem.infra.persistence.repository.WorkItemJpaRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CoverageSchedulerTest {

  @Mock private CoverageBucketJpaRepository coverageBucketJpaRepository;
  @Mock private CoverageBucketCountJpaRepository coverageBucketCountJpaRepository;
  @Mock private SummonerJpaRepository summonerJpaRepository;
  @Mock private IngestQueueStatsJpaRepository ingestQueueStatsJpaRepository;
  @Mock private WorkItemJpaRepository workItemJpaRepository;

  private CoverageScheduler scheduler;

  @BeforeEach
  void setUp() {
    scheduler = new CoverageScheduler(
        coverageBucketJpaRepository,
        coverageBucketCountJpaRepository,
        summonerJpaRepository,
        ingestQueueStatsJpaRepository,
        workItemJpaRepository
    );
  }

  @Test
  void scheduleCreatesVerifyWorkItemWhenVerifiedPoolIsLow() {
    CoverageBucket bucket = CoverageBucket.create("15.7", Tier.MASTER, 20000L, 1);
    given(coverageBucketJpaRepository.findAllByOrderByPriorityAscIdAsc()).willReturn(List.of(bucket));
    given(coverageBucketCountJpaRepository.countDistinctMatches("15.7", "MASTER")).willReturn(100L);
    given(summonerJpaRepository.countByLastKnownTier(Tier.MASTER)).willReturn(3L);
    given(workItemJpaRepository.existsByCoverageBucketIdAndTypeAndStatusIn(
        bucket.getId(), WorkItemType.VERIFY_SUMMONERS, List.of(WorkItemStatus.READY, WorkItemStatus.RUNNING)
    )).willReturn(false);
    given(workItemJpaRepository.save(org.mockito.ArgumentMatchers.any(WorkItem.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    CoverageScheduler.ScheduleResult result = scheduler.schedule();

    ArgumentCaptor<WorkItem> captor = ArgumentCaptor.forClass(WorkItem.class);
    org.mockito.Mockito.verify(workItemJpaRepository).save(captor.capture());
    assertThat(result.createdCount()).isEqualTo(1);
    assertThat(captor.getValue().getType()).isEqualTo(WorkItemType.VERIFY_SUMMONERS);
  }

  @Test
  void scheduleCreatesRefreshThenIngestThenSeedBasedOnBacklogSignals() {
    CoverageBucket bucket = CoverageBucket.create("15.7", Tier.DIAMOND, 20000L, 1);
    given(coverageBucketJpaRepository.findAllByOrderByPriorityAscIdAsc()).willReturn(List.of(bucket));
    given(coverageBucketCountJpaRepository.countDistinctMatches("15.7", "DIAMOND")).willReturn(100L);
    given(summonerJpaRepository.countByLastKnownTier(Tier.DIAMOND)).willReturn(100L);
    given(ingestQueueStatsJpaRepository.countReadyByTier("DIAMOND")).willReturn(0L);
    given(workItemJpaRepository.existsByCoverageBucketIdAndTypeAndStatusIn(
        bucket.getId(), WorkItemType.REFRESH_SUMMONERS, List.of(WorkItemStatus.READY, WorkItemStatus.RUNNING)
    )).willReturn(false);
    given(workItemJpaRepository.save(org.mockito.ArgumentMatchers.any(WorkItem.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    CoverageScheduler.ScheduleResult result = scheduler.schedule();

    assertThat(result.workItems()).singleElement().extracting(WorkItem::getType).isEqualTo(WorkItemType.REFRESH_SUMMONERS);
  }

  @Test
  void scheduleSkipsWhenSamePendingWorkItemAlreadyExists() {
    CoverageBucket bucket = CoverageBucket.create("15.7", Tier.MASTER, 20000L, 1);
    given(coverageBucketJpaRepository.findAllByOrderByPriorityAscIdAsc()).willReturn(List.of(bucket));
    given(coverageBucketCountJpaRepository.countDistinctMatches("15.7", "MASTER")).willReturn(100L);
    given(summonerJpaRepository.countByLastKnownTier(Tier.MASTER)).willReturn(3L);
    given(workItemJpaRepository.existsByCoverageBucketIdAndTypeAndStatusIn(
        bucket.getId(), WorkItemType.VERIFY_SUMMONERS, List.of(WorkItemStatus.READY, WorkItemStatus.RUNNING)
    )).willReturn(true);

    CoverageScheduler.ScheduleResult result = scheduler.schedule();

    assertThat(result.createdCount()).isZero();
    org.mockito.Mockito.verify(workItemJpaRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
  }
}
