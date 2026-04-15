package com.bestduo_BE.ingest.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.bestduo_BE.common.domain.model.IngestResult;
import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.ingest.application.port.MatchQueueDispatcher;
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

  private MatchIngestRunner useCase;

  @BeforeEach
  void setUp() {
    useCase = new MatchIngestRunner(queue, ingestMatchDetail);
  }

  @Test
  @DisplayName("execute — 요청된 tier를 큐 락에 전달하고 처리 결과를 반환한다")
  void executeForwardsRequestedTierToQueueLocking() {
    MatchQueueDispatcher.Item item = new MatchQueueDispatcher.Item("match-1", Tier.GOLD, 1, "15.23");
    given(queue.recoverStaleRunning(10)).willReturn(0);
    given(queue.pickAndLock(3, 2, 10, Tier.GOLD)).willReturn(List.of(item));
    given(ingestMatchDetail.execute("match-1", Tier.GOLD, "15.23"))
        .willReturn(new IngestResult(1, 10L));

    MatchIngestRunner.Result result = useCase.execute(3, Tier.GOLD);

    verify(queue).pickAndLock(3, 2, 10, Tier.GOLD);
    verify(ingestMatchDetail).execute("match-1", Tier.GOLD, "15.23");
    verify(queue).markDone("match-1");
    assertThat(result.picked()).isEqualTo(1);
    assertThat(result.done()).isEqualTo(1);
    assertThat(result.rawCreated()).isEqualTo(1);
  }

  @Test
  @DisplayName("execute — tier 미지정 시 ALL_TIERS를 사용한다")
  void defaultExecuteUsesAllTiers() {
    given(queue.recoverStaleRunning(10)).willReturn(0);
    given(queue.pickAndLock(1, 2, 10, Tier.ALL_TIERS)).willReturn(List.of());

    MatchIngestRunner.Result result = useCase.execute(1);

    verify(queue).pickAndLock(1, 2, 10, Tier.ALL_TIERS);
    assertThat(result.picked()).isZero();
    assertThat(result.processed()).isZero();
  }

  @Test
  @DisplayName("item.patch()를 ingestMatchDetail에 전달한다")
  void execute_forwardsPatchFromItemToIngestMatchDetail() {
    MatchQueueDispatcher.Item item = new MatchQueueDispatcher.Item("match-2", Tier.EMERALD, 2, "15.24");
    given(queue.recoverStaleRunning(10)).willReturn(0);
    given(queue.pickAndLock(1, 2, 10, Tier.EMERALD)).willReturn(List.of(item));
    given(ingestMatchDetail.execute("match-2", Tier.EMERALD, "15.24"))
        .willReturn(new IngestResult(2, 20L));

    useCase.execute(1, Tier.EMERALD);

    verify(ingestMatchDetail).execute("match-2", Tier.EMERALD, "15.24");
  }

  @Test
  @DisplayName("item.patch() null이면 null 전달")
  void execute_withNullPatch_forwardsNullToIngestMatchDetail() {
    MatchQueueDispatcher.Item item = new MatchQueueDispatcher.Item("match-3", Tier.GOLD, 1, null);
    given(queue.recoverStaleRunning(10)).willReturn(0);
    given(queue.pickAndLock(1, 2, 10, Tier.GOLD)).willReturn(List.of(item));
    given(ingestMatchDetail.execute("match-3", Tier.GOLD, null))
        .willReturn(new IngestResult(0, null));

    useCase.execute(1, Tier.GOLD);

    verify(ingestMatchDetail).execute("match-3", Tier.GOLD, null);
  }
}
