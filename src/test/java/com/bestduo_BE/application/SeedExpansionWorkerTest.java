package com.bestduo_BE.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

import com.bestduo_BE.application.port.MatchIdsFinder;
import com.bestduo_BE.application.port.MatchQueueEnqueuer;
import com.bestduo_BE.application.port.SummonerExpandQueue;
import com.bestduo_BE.domain.model.Tier;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SeedExpansionWorkerTest {

  @Mock
  private SummonerExpandQueue summonerExpandQueue;

  @Mock
  private MatchIdsFinder matchIdsFinder;

  @Mock
  private MatchQueueEnqueuer matchQueueEnqueuer;

  private SeedExpansionWorker worker;

  @BeforeEach
  void setUp() {
    worker = new SeedExpansionWorker(
        summonerExpandQueue,
        matchIdsFinder,
        matchQueueEnqueuer
    );
  }

  @Test
  void markRunningBeforeDoneEvenWhenNoMatchIsFetched() {
    given(summonerExpandQueue.findReadyPuuds(5)).willReturn(List.of("puuid-1"));
    given(matchIdsFinder.findRecentMatchIds("puuid-1", 10)).willReturn(Collections.emptyList());

    SeedExpansionWorker.ExpansionResult result = worker.execute(5, 10, Tier.ALL_TIERS);

    InOrder order = inOrder(summonerExpandQueue);
    order.verify(summonerExpandQueue).findReadyPuuds(5);
    order.verify(summonerExpandQueue).markExpandRunning("puuid-1");
    order.verify(summonerExpandQueue).markExpandDone("puuid-1");
    order.verifyNoMoreInteractions();
    assertThat(result.puuidPicked()).isEqualTo(1);
    assertThat(result.matchIdsFetched()).isZero();
    assertThat(result.matchIdsEnqueued()).isZero();
  }

  @Test
  void enqueueMatchIdsAndUpdateCounters() {
    given(summonerExpandQueue.findReadyPuuds(2)).willReturn(List.of("p-1"));
    given(matchIdsFinder.findRecentMatchIds("p-1", 3)).willReturn(List.of("m-1", "m-2"));

    SeedExpansionWorker.ExpansionResult result = worker.execute(2, 3, Tier.EMERALD);

    verify(matchQueueEnqueuer)
        .enqueueAllIdempotent(List.of("m-1", "m-2"), Tier.EMERALD, 60);
    verify(summonerExpandQueue).markExpandDone("p-1");
    assertThat(result.puuidPicked()).isEqualTo(1);
    assertThat(result.matchIdsFetched()).isEqualTo(2);
    assertThat(result.matchIdsEnqueued()).isEqualTo(2);
  }

  @Test
  void markErrorWhenFetchingMatchIdsFails() {
    given(summonerExpandQueue.findReadyPuuds(1)).willReturn(List.of("p-err"));
    given(matchIdsFinder.findRecentMatchIds("p-err", 1))
        .willThrow(new IllegalStateException("temporary"));

    SeedExpansionWorker.ExpansionResult result = worker.execute(1, 1, Tier.SILVER);

    InOrder order = inOrder(summonerExpandQueue);
    order.verify(summonerExpandQueue).markExpandRunning("p-err");
    order.verify(summonerExpandQueue).markExpandError("p-err");
    assertThat(result.matchIdsFetched()).isZero();
    assertThat(result.matchIdsEnqueued()).isZero();
  }
}
