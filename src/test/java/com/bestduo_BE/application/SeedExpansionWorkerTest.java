package com.bestduo_BE.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;

import com.bestduo_BE.application.port.MatchIdsFinder;
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
  private CollectMatchDetailAndSaveRaw collectMatchDetailAndSaveRaw;

  @Mock
  private ExpandSeedsFromMatch expandSeedsFromMatch;

  private SeedExpansionWorker worker;

  @BeforeEach
  void setUp() {
    worker = new SeedExpansionWorker(
        summonerExpandQueue,
        matchIdsFinder,
        collectMatchDetailAndSaveRaw,
        expandSeedsFromMatch
    );
  }

  @Test
  void markRunningBeforeDoneEvenWhenNoMatchIsFetched() {
    given(summonerExpandQueue.findReadyPuuds(5)).willReturn(List.of("puuid-1"));
    given(matchIdsFinder.findRecentMatchIds("puuid-1", 10)).willReturn(Collections.emptyList());

    worker.execute(5, 10, Tier.ALL_TIERS);

    InOrder order = inOrder(summonerExpandQueue);
    order.verify(summonerExpandQueue).findReadyPuuds(5);
    order.verify(summonerExpandQueue).markExpandRunning("puuid-1");
    order.verify(summonerExpandQueue).markExpandDone("puuid-1");
    order.verifyNoMoreInteractions();
  }

  @Test
  void processMatchesAndUpdateCountersAfterRunning() {
    given(summonerExpandQueue.findReadyPuuds(1)).willReturn(List.of("puuid-42"));
    given(matchIdsFinder.findRecentMatchIds("puuid-42", 3))
        .willReturn(List.of("m-1", "m-2"));
    given(collectMatchDetailAndSaveRaw.execute("m-1", Tier.CHALLENGER)).willReturn(2);
    given(collectMatchDetailAndSaveRaw.execute("m-2", Tier.CHALLENGER)).willReturn(3);
    given(expandSeedsFromMatch.execute("m-1")).willReturn(4);
    given(expandSeedsFromMatch.execute("m-2")).willReturn(5);

    SeedExpansionWorker.ExpansionResult result = worker.execute(1, 3, Tier.CHALLENGER);

    InOrder order = inOrder(summonerExpandQueue);
    order.verify(summonerExpandQueue).findReadyPuuds(1);
    order.verify(summonerExpandQueue).markExpandRunning("puuid-42");
    order.verify(summonerExpandQueue).markExpandDone("puuid-42");
    order.verifyNoMoreInteractions();

    assertThat(result.puuidPicked()).isEqualTo(1);
    assertThat(result.matchIdsFetched()).isEqualTo(2);
    assertThat(result.rawCreated()).isEqualTo(5);
    assertThat(result.seedsExpanded()).isEqualTo(9);
  }
}
