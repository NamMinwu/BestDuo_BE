package com.bestduo_BE.pipeline.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.bestduo_BE.common.application.PatchVersionService;
import com.bestduo_BE.common.application.port.MatchIdsFinder;
import com.bestduo_BE.common.application.port.MatchQueueEnqueuer;
import com.bestduo_BE.common.domain.model.EffectivePatchContext;
import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.persistence.entity.Summoner;
import com.bestduo_BE.common.infra.persistence.repository.SummonerJpaRepository;
import com.bestduo_BE.common.infra.riot.budget.DailyBudgetTracker;
import com.bestduo_BE.common.infra.riot.exception.RiotRateLimitedException;
import com.bestduo_BE.config.PipelineProperties;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CollectMatchIdsRunnerTest {

  @Mock
  private SummonerJpaRepository summonerRepository;

  @Mock
  private MatchIdsFinder matchIdsFinder;

  @Mock
  private MatchQueueEnqueuer matchQueueEnqueuer;

  @Mock
  private PatchVersionService patchVersionService;

  @Mock
  private DailyBudgetTracker budgetTracker;

  private PipelineProperties props;
  private CollectMatchIdsRunner runner;

  @BeforeEach
  void setUp() {
    props = new PipelineProperties();
    props.setCollectBatchSize(3);
    PipelineProperties.TierMatchCount tierMatchCount = new PipelineProperties.TierMatchCount();
    tierMatchCount.setApexTiers(30);
    tierMatchCount.setDiamondEmerald(10);
    props.setTierMatchCount(tierMatchCount);
    runner = new CollectMatchIdsRunner(
        summonerRepository, matchIdsFinder, matchQueueEnqueuer,
        patchVersionService, budgetTracker, props);
  }

  // ── hasPending ──────────────────────────────────────────────────────────

  @Test
  @DisplayName("collect 예산 소진 시 hasPending는 false")
  void hasPending_whenBudgetExhausted_returnsFalse() {
    given(budgetTracker.canCollect()).willReturn(false);

    assertThat(runner.hasPending()).isFalse();
  }

  @Test
  @DisplayName("대기 중인 summoner가 없으면 hasPending는 false")
  void hasPending_whenNoSummoners_returnsFalse() {
    given(budgetTracker.canCollect()).willReturn(true);
    given(summonerRepository.findMatchIdsPendingSummoners(1)).willReturn(List.of());

    assertThat(runner.hasPending()).isFalse();
  }

  @Test
  @DisplayName("대기 중인 summoner가 있으면 hasPending는 true")
  void hasPending_whenSummonersExist_returnsTrue() {
    given(budgetTracker.canCollect()).willReturn(true);
    given(summonerRepository.findMatchIdsPendingSummoners(1))
        .willReturn(List.of(summonerWithTier("p1", Tier.DIAMOND)));

    assertThat(runner.hasPending()).isTrue();
  }

  // ── runBatch ────────────────────────────────────────────────────────────

  @Test
  @DisplayName("예산 소진 시 runBatch는 BUDGET_EXHAUSTED 반환")
  void runBatch_whenBudgetExhausted_returnsBudgetExhausted() {
    given(budgetTracker.canCollect()).willReturn(false);

    CollectMatchIdsRunner.BatchResult result = runner.runBatch();

    assertThat(result.type()).isEqualTo(CollectMatchIdsRunner.BatchResult.Type.BUDGET_EXHAUSTED);
    verify(summonerRepository, never()).findMatchIdsPendingSummoners(anyInt());
  }

  @Test
  @DisplayName("대기 summoner 없으면 NO_PENDING 반환")
  void runBatch_whenNoPending_returnsNoPending() {
    given(budgetTracker.canCollect()).willReturn(true);
    given(summonerRepository.findMatchIdsPendingSummoners(props.getCollectBatchSize()))
        .willReturn(List.of());

    CollectMatchIdsRunner.BatchResult result = runner.runBatch();

    assertThat(result.type()).isEqualTo(CollectMatchIdsRunner.BatchResult.Type.NO_PENDING);
  }

  @Test
  @DisplayName("정상 패치(grace period 아님): DIAMOND tier summoner에게 findMatchIdsSince 호출")
  void runBatch_normalPatch_usesCorrectMatchCountForDiamond() {
    Summoner summoner = summonerWithTier("p-dia", Tier.DIAMOND);
    EffectivePatchContext ctx = new EffectivePatchContext("15.23", 1700000000L, null);
    given(budgetTracker.canCollect()).willReturn(true);
    given(summonerRepository.findMatchIdsPendingSummoners(props.getCollectBatchSize()))
        .willReturn(List.of(summoner));
    given(patchVersionService.resolveEffectivePatchContext()).willReturn(Optional.of(ctx));
    given(matchIdsFinder.findMatchIdsSince("p-dia", 1700000000L, 10))
        .willReturn(List.of("m1", "m2"));

    CollectMatchIdsRunner.BatchResult result = runner.runBatch();

    verify(matchIdsFinder).findMatchIdsSince("p-dia", 1700000000L, 10);
    verify(matchIdsFinder, never()).findMatchIdsBetween(any(), anyLong(), anyLong(), anyInt());
    assertThat(result.matchIdsQueued()).isEqualTo(2);
  }

  @Test
  @DisplayName("정상 패치(grace period 아님): CHALLENGER tier summoner에게 apexTiers matchCount 적용")
  void runBatch_normalPatch_usesApexMatchCountForChallenger() {
    Summoner summoner = summonerWithTier("p-chall", Tier.CHALLENGER);
    EffectivePatchContext ctx = new EffectivePatchContext("15.23", 1700000000L, null);
    given(budgetTracker.canCollect()).willReturn(true);
    given(summonerRepository.findMatchIdsPendingSummoners(props.getCollectBatchSize()))
        .willReturn(List.of(summoner));
    given(patchVersionService.resolveEffectivePatchContext()).willReturn(Optional.of(ctx));
    given(matchIdsFinder.findMatchIdsSince("p-chall", 1700000000L, 30))
        .willReturn(List.of("m1", "m2", "m3"));

    runner.runBatch();

    verify(matchIdsFinder).findMatchIdsSince("p-chall", 1700000000L, 30);
  }

  @Test
  @DisplayName("grace period 중: findMatchIdsBetween이 endTime과 함께 호출된다")
  void runBatch_inGracePeriod_callsFindMatchIdsBetweenWithEndTime() {
    Summoner summoner = summonerWithTier("p-dia", Tier.DIAMOND);
    EffectivePatchContext ctx = new EffectivePatchContext("16.7", 1699000000L, 1700000000L);
    given(budgetTracker.canCollect()).willReturn(true);
    given(summonerRepository.findMatchIdsPendingSummoners(props.getCollectBatchSize()))
        .willReturn(List.of(summoner));
    given(patchVersionService.resolveEffectivePatchContext()).willReturn(Optional.of(ctx));
    given(matchIdsFinder.findMatchIdsBetween("p-dia", 1699000000L, 1700000000L, 10))
        .willReturn(List.of("m1", "m2"));

    CollectMatchIdsRunner.BatchResult result = runner.runBatch();

    verify(matchIdsFinder).findMatchIdsBetween("p-dia", 1699000000L, 1700000000L, 10);
    verify(matchIdsFinder, never()).findMatchIdsSince(any(), anyLong(), anyInt());
    assertThat(result.matchIdsQueued()).isEqualTo(2);
  }

  @Test
  @DisplayName("grace period 중: MatchQueue는 이전 패치(16.7)로 태깅된다")
  void runBatch_inGracePeriod_enqueuedWithPreviousPatchTag() {
    Summoner summoner = summonerWithTier("p-dia", Tier.DIAMOND);
    EffectivePatchContext ctx = new EffectivePatchContext("16.7", 1699000000L, 1700000000L);
    given(budgetTracker.canCollect()).willReturn(true);
    given(summonerRepository.findMatchIdsPendingSummoners(props.getCollectBatchSize()))
        .willReturn(List.of(summoner));
    given(patchVersionService.resolveEffectivePatchContext()).willReturn(Optional.of(ctx));
    given(matchIdsFinder.findMatchIdsBetween(any(), anyLong(), anyLong(), anyInt()))
        .willReturn(List.of("m1"));

    runner.runBatch();

    verify(matchQueueEnqueuer).enqueueAllIdempotent(any(), any(), anyInt(), eq("16.7"));
  }

  @Test
  @DisplayName("matchIds를 수집한 후 matchIdsCollectedAt을 갱신한다")
  void runBatch_marksMatchIdsCollectedAfterCollection() {
    Summoner summoner = summonerWithTier("p-1", Tier.EMERALD);
    EffectivePatchContext ctx = new EffectivePatchContext("15.23", 1700000000L, null);
    given(budgetTracker.canCollect()).willReturn(true);
    given(summonerRepository.findMatchIdsPendingSummoners(props.getCollectBatchSize()))
        .willReturn(List.of(summoner));
    given(patchVersionService.resolveEffectivePatchContext()).willReturn(Optional.of(ctx));
    given(matchIdsFinder.findMatchIdsSince(eq("p-1"), anyLong(), anyInt()))
        .willReturn(List.of("m1"));

    runner.runBatch();

    verify(summonerRepository).markMatchIdsCollected(eq("p-1"), any(OffsetDateTime.class));
    verify(budgetTracker).recordCollectCall(1);
  }

  @Test
  @DisplayName("패치 정보가 없으면 findRecentMatchIds를 사용")
  void runBatch_whenNoPatchContext_usesRecentMatchIds() {
    Summoner summoner = summonerWithTier("p-gold", Tier.GOLD);
    given(budgetTracker.canCollect()).willReturn(true);
    given(summonerRepository.findMatchIdsPendingSummoners(props.getCollectBatchSize()))
        .willReturn(List.of(summoner));
    given(patchVersionService.resolveEffectivePatchContext()).willReturn(Optional.empty());
    given(matchIdsFinder.findRecentMatchIds("p-gold", 10)).willReturn(List.of("m1"));

    runner.runBatch();

    verify(matchIdsFinder).findRecentMatchIds("p-gold", 10);
    verify(matchIdsFinder, never()).findMatchIdsSince(any(), anyLong(), anyInt());
    verify(matchIdsFinder, never()).findMatchIdsBetween(any(), anyLong(), anyLong(), anyInt());
  }

  @Test
  @DisplayName("matchIds 수집 중 개별 summoner 오류는 건너뛰고 계속 진행")
  void runBatch_skipsSummonerOnIndividualError() {
    Summoner bad = summonerWithTier("p-bad", Tier.GOLD);
    Summoner good = summonerWithTier("p-good", Tier.GOLD);
    EffectivePatchContext ctx = new EffectivePatchContext("15.23", 1700000000L, null);
    given(budgetTracker.canCollect()).willReturn(true);
    given(summonerRepository.findMatchIdsPendingSummoners(props.getCollectBatchSize()))
        .willReturn(List.of(bad, good));
    given(patchVersionService.resolveEffectivePatchContext()).willReturn(Optional.of(ctx));
    given(matchIdsFinder.findMatchIdsSince(eq("p-bad"), anyLong(), anyInt()))
        .willThrow(new RuntimeException("API error"));
    given(matchIdsFinder.findMatchIdsSince(eq("p-good"), anyLong(), anyInt()))
        .willReturn(List.of("m1"));

    CollectMatchIdsRunner.BatchResult result = runner.runBatch();

    verify(summonerRepository).markMatchIdsCollected(eq("p-good"), any());
    verify(summonerRepository, never()).markMatchIdsCollected(eq("p-bad"), any());
    assertThat(result.apiCalls()).isEqualTo(2);
  }

  @Test
  @DisplayName("429 예외는 전파된다")
  void runBatch_propagatesRateLimitedException() {
    Summoner summoner = summonerWithTier("p-1", Tier.GOLD);
    EffectivePatchContext ctx = new EffectivePatchContext("15.23", 1700000000L, null);
    given(budgetTracker.canCollect()).willReturn(true);
    given(summonerRepository.findMatchIdsPendingSummoners(props.getCollectBatchSize()))
        .willReturn(List.of(summoner));
    given(patchVersionService.resolveEffectivePatchContext()).willReturn(Optional.of(ctx));
    given(matchIdsFinder.findMatchIdsSince(any(), anyLong(), anyInt()))
        .willThrow(new RiotRateLimitedException("429"));

    assertThatThrownBy(() -> runner.runBatch())
        .isInstanceOf(RiotRateLimitedException.class);
  }

  @Test
  @DisplayName("matchIds가 비어있으면 enqueue 하지 않지만 collectedAt은 갱신")
  void runBatch_whenNoMatchIds_doesNotEnqueueButMarksCollected() {
    Summoner summoner = summonerWithTier("p-empty", Tier.GOLD);
    EffectivePatchContext ctx = new EffectivePatchContext("15.23", 1700000000L, null);
    given(budgetTracker.canCollect()).willReturn(true);
    given(summonerRepository.findMatchIdsPendingSummoners(props.getCollectBatchSize()))
        .willReturn(List.of(summoner));
    given(patchVersionService.resolveEffectivePatchContext()).willReturn(Optional.of(ctx));
    given(matchIdsFinder.findMatchIdsSince(any(), anyLong(), anyInt())).willReturn(List.of());

    runner.runBatch();

    verify(matchQueueEnqueuer, never()).enqueueAllIdempotent(any(), any(), anyInt(), any());
    verify(summonerRepository).markMatchIdsCollected(eq("p-empty"), any(OffsetDateTime.class));
  }

  // ── helpers ─────────────────────────────────────────────────────────────

  private Summoner summonerWithTier(String puuid, Tier tier) {
    return Summoner.builder()
        .puuid(puuid)
        .lastKnownTier(tier)
        .leagueEntryFetchedAt(OffsetDateTime.now().minusHours(1))
        .createdAt(OffsetDateTime.now())
        .updatedAt(OffsetDateTime.now())
        .build();
  }
}
