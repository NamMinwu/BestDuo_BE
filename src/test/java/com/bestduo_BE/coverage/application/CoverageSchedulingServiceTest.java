package com.bestduo_BE.coverage.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.persistence.repository.IngestQueueStatsJpaRepository;
import com.bestduo_BE.common.infra.persistence.repository.SummonerJpaRepository;
import com.bestduo_BE.config.WorkItemProperties;
import com.bestduo_BE.coverage.infra.persistence.entity.CoverageBucket;
import com.bestduo_BE.coverage.infra.persistence.repository.CoverageBucketCountJpaRepository;
import com.bestduo_BE.coverage.infra.persistence.repository.CoverageBucketJpaRepository;
import com.bestduo_BE.workitem.application.port.WorkItemDispatcher;
import com.bestduo_BE.workitem.domain.model.WorkItemStatus;
import com.bestduo_BE.workitem.domain.model.WorkItemType;
import com.bestduo_BE.workitem.infra.persistence.entity.WorkItem;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CoverageSchedulingServiceTest {

  @Mock private CoverageBucketJpaRepository coverageBucketJpaRepository;
  @Mock private CoverageBucketCountJpaRepository coverageBucketCountRepository;
  @Mock private SummonerJpaRepository summonerJpaRepository;
  @Mock private IngestQueueStatsJpaRepository ingestQueueStatsJpaRepository;
  @Mock private WorkItemDispatcher workItemDispatcher;

  private CoverageSchedulingService service;

  @BeforeEach
  void setUp() {
    service = new CoverageSchedulingService(
        coverageBucketJpaRepository,
        coverageBucketCountRepository,
        summonerJpaRepository,
        ingestQueueStatsJpaRepository,
        workItemDispatcher,
        new WorkItemProperties()
    );
  }

  @Test
  void scheduleCreatesSeedWhenNoSummonersExist() {
    CoverageBucket bucket = CoverageBucket.create("15.7", Tier.MASTER, 20_000L, 1);
    given(coverageBucketJpaRepository.findAllByOrderByPriorityAscIdAsc()).willReturn(List.of(bucket));
    given(coverageBucketCountRepository.countDistinctMatches("15.7", "MASTER")).willReturn(0L);
    given(summonerJpaRepository.countByLastKnownTier(Tier.MASTER)).willReturn(0L);
    given(workItemDispatcher.countByTypePatchTierStatuses(WorkItemType.SEED_SUMMONERS, "15.7", Tier.MASTER,
        List.of(WorkItemStatus.PENDING, WorkItemStatus.RUNNING)))
        .willReturn(0L);
    given(workItemDispatcher.emit(org.mockito.ArgumentMatchers.any(WorkItem.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    CoverageScheduler.ScheduleResult result = service.schedule();

    ArgumentCaptor<WorkItem> captor = ArgumentCaptor.forClass(WorkItem.class);
    org.mockito.Mockito.verify(workItemDispatcher).emit(captor.capture());
    assertThat(result.createdCount()).isEqualTo(1);
    assertThat(captor.getValue().getType()).isEqualTo(WorkItemType.SEED_SUMMONERS);
  }

  @Test
  void scheduleCreatesSeedWhenVerifiedPoolIsLow() {
    CoverageBucket bucket = CoverageBucket.create("15.7", Tier.MASTER, 20_000L, 1);
    given(coverageBucketJpaRepository.findAllByOrderByPriorityAscIdAsc()).willReturn(List.of(bucket));
    given(coverageBucketCountRepository.countDistinctMatches("15.7", "MASTER")).willReturn(100L);
    given(summonerJpaRepository.countByLastKnownTier(Tier.MASTER)).willReturn(3L);
    given(workItemDispatcher.countByTypePatchTierStatuses(WorkItemType.SEED_SUMMONERS, "15.7", Tier.MASTER,
        List.of(WorkItemStatus.PENDING, WorkItemStatus.RUNNING)))
        .willReturn(0L);
    given(workItemDispatcher.emit(org.mockito.ArgumentMatchers.any(WorkItem.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    CoverageScheduler.ScheduleResult result = service.schedule();

    ArgumentCaptor<WorkItem> captor = ArgumentCaptor.forClass(WorkItem.class);
    org.mockito.Mockito.verify(workItemDispatcher).emit(captor.capture());
    assertThat(result.createdCount()).isEqualTo(1);
    assertThat(captor.getValue().getType()).isEqualTo(WorkItemType.SEED_SUMMONERS);
    assertThat(captor.getValue().getStatus()).isEqualTo(WorkItemStatus.PENDING);
  }

  @Test
  void scheduleCreatesRefreshWhenReadyQueueIsLow() {
    CoverageBucket bucket = CoverageBucket.create("15.7", Tier.DIAMOND, 20_000L, 1);
    given(coverageBucketJpaRepository.findAllByOrderByPriorityAscIdAsc()).willReturn(List.of(bucket));
    given(coverageBucketCountRepository.countDistinctMatches("15.7", "DIAMOND")).willReturn(100L);
    given(summonerJpaRepository.countByLastKnownTier(Tier.DIAMOND)).willReturn(100L);
    given(ingestQueueStatsJpaRepository.countReadyByTier("DIAMOND")).willReturn(0L);
    given(workItemDispatcher.countByTypePatchTierStatuses(WorkItemType.REFRESH_SUMMONERS, "15.7", Tier.DIAMOND,
        List.of(WorkItemStatus.PENDING, WorkItemStatus.RUNNING)))
        .willReturn(0L);
    given(workItemDispatcher.emit(org.mockito.ArgumentMatchers.any(WorkItem.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    CoverageScheduler.ScheduleResult result = service.schedule();

    assertThat(result.workItems()).singleElement().extracting(WorkItem::getType).isEqualTo(WorkItemType.REFRESH_SUMMONERS);
  }

  @Test
  void scheduleSkipsWhenSamePendingWorkItemAlreadyExists() {
    CoverageBucket bucket = CoverageBucket.create("15.7", Tier.MASTER, 20_000L, 1);
    given(coverageBucketJpaRepository.findAllByOrderByPriorityAscIdAsc()).willReturn(List.of(bucket));
    given(coverageBucketCountRepository.countDistinctMatches("15.7", "MASTER")).willReturn(100L);
    given(summonerJpaRepository.countByLastKnownTier(Tier.MASTER)).willReturn(3L);
    given(workItemDispatcher.countByTypePatchTierStatuses(WorkItemType.SEED_SUMMONERS, "15.7", Tier.MASTER,
        List.of(WorkItemStatus.PENDING, WorkItemStatus.RUNNING)))
        .willReturn(1L);

    CoverageScheduler.ScheduleResult result = service.schedule();

    assertThat(result.createdCount()).isZero();
    org.mockito.Mockito.verify(workItemDispatcher, org.mockito.Mockito.never()).emit(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void scheduleSkipsWhenSameRunningWorkItemAlreadyExists() {
    CoverageBucket bucket = CoverageBucket.create("15.7", Tier.MASTER, 20_000L, 1);
    given(coverageBucketJpaRepository.findAllByOrderByPriorityAscIdAsc()).willReturn(List.of(bucket));
    given(coverageBucketCountRepository.countDistinctMatches("15.7", "MASTER")).willReturn(100L);
    given(summonerJpaRepository.countByLastKnownTier(Tier.MASTER)).willReturn(3L);
    given(workItemDispatcher.countByTypePatchTierStatuses(WorkItemType.SEED_SUMMONERS, "15.7", Tier.MASTER,
        List.of(WorkItemStatus.PENDING, WorkItemStatus.RUNNING))).willReturn(1L);

    CoverageScheduler.ScheduleResult result = service.schedule();

    assertThat(result.createdCount()).isZero();
    org.mockito.Mockito.verify(workItemDispatcher, org.mockito.Mockito.never()).emit(org.mockito.ArgumentMatchers.any());
  }
}
