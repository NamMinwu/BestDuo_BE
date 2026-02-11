package com.bestduo_BE.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.bestduo_BE.domain.model.Tier;
import com.bestduo_BE.infra.persistence.entity.Summoner;
import com.bestduo_BE.infra.persistence.repository.SummonerJpaRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshBatchRunTest {

  @Mock
  private SummonerJpaRepository summonerJpaRepository;

  @Mock
  private RefreshSummonerMatches refreshSummonerMatches;

  private RefreshBatchRun useCase;

  @BeforeEach
  void setUp() {
    useCase = new RefreshBatchRun(summonerJpaRepository, refreshSummonerMatches);
  }

  @Test
  void aggregateSuccessAndFailureCounts() {
    List<Summoner> targets = List.of(summoner("p1"), summoner("p2"), summoner("p3"));
    given(summonerJpaRepository.findRefreshTargets(3)).willReturn(targets);
    given(refreshSummonerMatches.execute("p1"))
        .willReturn(new RefreshSummonerMatches.Result("p1", 2, Tier.GOLD, 1L));
    given(refreshSummonerMatches.execute("p2"))
        .willReturn(new RefreshSummonerMatches.Result("p2", 5, Tier.SILVER, 2L));
    given(refreshSummonerMatches.execute("p3"))
        .willThrow(new IllegalStateException("down"));

    RefreshBatchRun.Result result = useCase.execute(3);

    verify(summonerJpaRepository).findRefreshTargets(3);
    assertThat(result.processed()).isEqualTo(3);
    assertThat(result.success()).isEqualTo(2);
    assertThat(result.failed()).isEqualTo(1);
    assertThat(result.matchIdsEnqueued()).isEqualTo(7);
  }

  @Test
  void returnZerosWhenNoTargetsFound() {
    given(summonerJpaRepository.findRefreshTargets(5)).willReturn(List.of());

    RefreshBatchRun.Result result = useCase.execute(5);

    assertThat(result.processed()).isZero();
    assertThat(result.success()).isZero();
    assertThat(result.failed()).isZero();
    assertThat(result.matchIdsEnqueued()).isZero();
  }

  private Summoner summoner(String puuid) {
    OffsetDateTime now = OffsetDateTime.now();
    return Summoner.builder()
        .puuid(puuid)
        .seedStatus("READY")
        .expandStatus("READY")
        .refreshStatus("READY")
        .createdAt(now)
        .updatedAt(now)
        .build();
  }
}
