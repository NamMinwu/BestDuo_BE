package com.bestduo_BE.refresh.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.persistence.entity.Summoner;
import com.bestduo_BE.common.infra.persistence.repository.SummonerJpaRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshBatchExecutorTest {

  @Mock
  private SummonerJpaRepository summonerJpaRepository;

  @Mock
  private RefreshSummonerMatches refreshSummonerMatches;

  private RefreshBatchExecutor useCase;

  @BeforeEach
  void setUp() {
    useCase = new RefreshBatchExecutor(summonerJpaRepository, refreshSummonerMatches);
  }

  @Test
  void aggregateSuccessAndFailureCounts() {
    List<Summoner> targets = List.of(summoner("p1"), summoner("p2"), summoner("p3"));
    given(summonerJpaRepository.findRefreshTargets(3)).willReturn(targets);
    given(refreshSummonerMatches.execute("p1", Tier.GOLD))
        .willReturn(new RefreshSummonerMatches.Result("p1", 2, Tier.GOLD, 1L));
    given(refreshSummonerMatches.execute("p2", Tier.GOLD))
        .willReturn(new RefreshSummonerMatches.Result("p2", 5, Tier.SILVER, 2L));
    given(refreshSummonerMatches.execute("p3", Tier.GOLD))
        .willThrow(new IllegalStateException("down"));

    RefreshBatchExecutor.Result result = useCase.execute(3, Tier.GOLD);

    verify(summonerJpaRepository).findRefreshTargets(3);
    assertThat(result.processed()).isEqualTo(3);
    assertThat(result.success()).isEqualTo(2);
    assertThat(result.failed()).isEqualTo(1);
    assertThat(result.matchIdsEnqueued()).isEqualTo(7);
  }

  @Test
  void returnZerosWhenNoTargetsFound() {
    given(summonerJpaRepository.findRefreshTargets(5)).willReturn(List.of());

    RefreshBatchExecutor.Result result = useCase.execute(5, Tier.ALL_TIERS);

    assertThat(result.processed()).isZero();
    assertThat(result.success()).isZero();
    assertThat(result.failed()).isZero();
    assertThat(result.matchIdsEnqueued()).isZero();
  }

  @Test
  void defaultExecuteUsesAllTiers() {
    given(summonerJpaRepository.findRefreshTargets(1)).willReturn(List.of(summoner("p1")));
    given(refreshSummonerMatches.execute("p1", Tier.ALL_TIERS))
        .willReturn(new RefreshSummonerMatches.Result("p1", 1, Tier.GOLD, 1L));

    RefreshBatchExecutor.Result result = useCase.execute(1);

    verify(refreshSummonerMatches).execute("p1", Tier.ALL_TIERS);
    assertThat(result.matchIdsEnqueued()).isEqualTo(1);
  }

  @Test
  void stillForwardsRequestedTierToRefreshWorker() {
    given(summonerJpaRepository.findRefreshTargets(2)).willReturn(List.of(summoner("p1")));
    given(refreshSummonerMatches.execute("p1", Tier.EMERALD))
        .willReturn(new RefreshSummonerMatches.Result("p1", 1, Tier.EMERALD, 1L));

    RefreshBatchExecutor.Result result = useCase.execute(2, Tier.EMERALD);

    verify(summonerJpaRepository).findRefreshTargets(2);
    verify(refreshSummonerMatches).execute("p1", Tier.EMERALD);
    assertThat(result.success()).isEqualTo(1);
  }

  private Summoner summoner(String puuid) {
    OffsetDateTime now = OffsetDateTime.now();
    return Summoner.builder()
        .puuid(puuid)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }
}
