package com.bestduo_BE.ingest.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.bestduo_BE.common.domain.model.IngestResult;
import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.ingest.application.port.MatchQueueDispatcher;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MatchIngestWorkerTest {

  @Mock
  private MatchQueueDispatcher queue;

  @Mock
  private IngestMatchDetail ingestMatchDetail;

  private MatchIngestWorker useCase;

  @BeforeEach
  void setUp() {
    useCase = new MatchIngestWorker(queue, ingestMatchDetail);
  }

  @Test
  void executeForwardsRequestedTierToQueueLocking() {
    MatchQueueDispatcher.Item item = new MatchQueueDispatcher.Item("match-1", Tier.GOLD, 1, "15.23");
    given(queue.recoverStaleRunning(10)).willReturn(0);
    given(queue.pickAndLock(3, 2, 10, Tier.GOLD)).willReturn(List.of(item));
    given(ingestMatchDetail.execute("match-1", Tier.GOLD))
        .willReturn(new IngestResult(1, 10L));

    MatchIngestWorker.Result result = useCase.execute(3, Tier.GOLD);

    verify(queue).pickAndLock(3, 2, 10, Tier.GOLD);
    verify(queue).markDone("match-1");
    assertThat(result.picked()).isEqualTo(1);
    assertThat(result.done()).isEqualTo(1);
    assertThat(result.rawCreated()).isEqualTo(1);
  }

  @Test
  void defaultExecuteUsesAllTiers() {
    given(queue.recoverStaleRunning(10)).willReturn(0);
    given(queue.pickAndLock(1, 2, 10, Tier.ALL_TIERS)).willReturn(List.of());

    MatchIngestWorker.Result result = useCase.execute(1);

    verify(queue).pickAndLock(1, 2, 10, Tier.ALL_TIERS);
    assertThat(result.picked()).isZero();
    assertThat(result.processed()).isZero();
  }
}
