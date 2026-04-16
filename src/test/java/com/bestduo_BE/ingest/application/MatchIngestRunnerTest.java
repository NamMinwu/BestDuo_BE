package com.bestduo_BE.ingest.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.bestduo_BE.common.domain.model.IngestResult;
import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.config.PipelineProperties;
import com.bestduo_BE.ingest.infra.persistence.MatchQueueDispatcher;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MatchIngestRunnerTest {

  @Mock
  private MatchQueueDispatcher queue;

  @Mock
  private IngestMatchDetail ingestMatchDetail;

  private PipelineProperties props;
  private MatchIngestRunner useCase;

  @BeforeEach
  void setUp() {
    props = new PipelineProperties();
    useCase = new MatchIngestRunner(queue, ingestMatchDetail, props);
  }

  @Test
  @DisplayName("executeWithPriority — 요청된 tier를 큐 락에 전달하고 처리 결과를 반환한다")
  void executeForwardsRequestedTierToQueueLocking() {
    MatchQueueDispatcher.Item item = new MatchQueueDispatcher.Item("match-1", Tier.GOLD, 1, "15.23");
    given(queue.recoverStaleRunning(10)).willReturn(0);
    given(queue.pickAndLockWithPriority(3, 2, 10, Tier.GOLD, null)).willReturn(List.of(item));
    given(ingestMatchDetail.execute("match-1", Tier.GOLD, "15.23"))
        .willReturn(new IngestResult(1, 10L));

    MatchIngestRunner.Result result = useCase.executeWithPriority(3, Tier.GOLD, null);

    verify(queue).pickAndLockWithPriority(3, 2, 10, Tier.GOLD, null);
    verify(ingestMatchDetail).execute("match-1", Tier.GOLD, "15.23");
    verify(queue).markDone("match-1");
    assertThat(result.picked()).isEqualTo(1);
    assertThat(result.done()).isEqualTo(1);
    assertThat(result.rawCreated()).isEqualTo(1);
  }

  @Test
  @DisplayName("tier null이면 모든 tier를 대상으로 큐를 조회한다")
  void nullTierQueriesAllTiers() {
    given(queue.recoverStaleRunning(10)).willReturn(0);
    given(queue.pickAndLockWithPriority(1, 2, 10, null, null)).willReturn(List.of());

    MatchIngestRunner.Result result = useCase.executeWithPriority(1, null, null);

    verify(queue).pickAndLockWithPriority(1, 2, 10, null, null);
    assertThat(result.picked()).isZero();
    assertThat(result.processed()).isZero();
  }

  @Test
  @DisplayName("item.patch()를 ingestMatchDetail에 전달한다")
  void execute_forwardsPatchFromItemToIngestMatchDetail() {
    MatchQueueDispatcher.Item item = new MatchQueueDispatcher.Item("match-2", Tier.EMERALD, 2, "15.24");
    given(queue.recoverStaleRunning(10)).willReturn(0);
    given(queue.pickAndLockWithPriority(1, 2, 10, Tier.EMERALD, null)).willReturn(List.of(item));
    given(ingestMatchDetail.execute("match-2", Tier.EMERALD, "15.24"))
        .willReturn(new IngestResult(2, 20L));

    useCase.executeWithPriority(1, Tier.EMERALD, null);

    verify(ingestMatchDetail).execute("match-2", Tier.EMERALD, "15.24");
  }

  @Test
  @DisplayName("item.patch() null이면 null 전달")
  void execute_withNullPatch_forwardsNullToIngestMatchDetail() {
    MatchQueueDispatcher.Item item = new MatchQueueDispatcher.Item("match-3", Tier.GOLD, 1, null);
    given(queue.recoverStaleRunning(10)).willReturn(0);
    given(queue.pickAndLockWithPriority(1, 2, 10, Tier.GOLD, null)).willReturn(List.of(item));
    given(ingestMatchDetail.execute("match-3", Tier.GOLD, null))
        .willReturn(new IngestResult(0, null));

    useCase.executeWithPriority(1, Tier.GOLD, null);

    verify(ingestMatchDetail).execute("match-3", Tier.GOLD, null);
  }
}
