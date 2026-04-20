package com.bestduo_BE.pipeline.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.bestduo_BE.common.application.PatchVersionService;
import com.bestduo_BE.common.domain.model.EffectivePatchContext;
import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.riot.exception.RiotRateLimitedException;
import com.bestduo_BE.config.PipelineProperties;
import com.bestduo_BE.ingest.application.MatchIngestRunner;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PipelineRunnerTest {

  @Mock private DailyLeagueEntriesRunner dailyLeagueEntriesRunner;
  @Mock private CollectMatchIdsRunner collectMatchIdsRunner;
  @Mock private MatchIngestRunner matchIngestRunner;
  @Mock private PatchVersionService patchVersionService;

  private PipelineProperties props;
  private PipelineRunner runner;

  @BeforeEach
  void setUp() {
    props = new PipelineProperties();
    props.setIngestBatchSize(10);
    props.setPollingIntervalMs(100);
    PipelineMetrics pipelineMetrics = new PipelineMetrics(new SimpleMeterRegistry());
    runner = new PipelineRunner(dailyLeagueEntriesRunner, collectMatchIdsRunner, matchIngestRunner,
        patchVersionService, props, pipelineMetrics);
  }

  @Test
  @DisplayName("Stage 1 작업이 있으면 runNextChunk만 호출된다")
  void executeTick_whenStage1HasWork_runsOnlyStage1() throws InterruptedException {
    given(dailyLeagueEntriesRunner.hasWorkToday()).willReturn(true);

    runner.executeTick();

    verify(dailyLeagueEntriesRunner).runNextChunk();
    verify(collectMatchIdsRunner, never()).runBatch();
    verify(matchIngestRunner, never()).executeWithPriority(anyInt(), any(), any());
  }

  @Test
  @DisplayName("Stage 1 없고 Stage 2 대기 중이면 runBatch만 호출된다")
  void executeTick_whenStage2HasPending_runsOnlyStage2() throws InterruptedException {
    given(dailyLeagueEntriesRunner.hasWorkToday()).willReturn(false);
    given(collectMatchIdsRunner.hasPending()).willReturn(true);

    runner.executeTick();

    verify(collectMatchIdsRunner).runBatch();
    verify(matchIngestRunner, never()).executeWithPriority(anyInt(), any(), any());
  }

  @Test
  @DisplayName("유예 기간이 아닐 때 Stage 3는 최신 패치(유효 패치 == 최신 패치)로 우선순위를 결정한다")
  void executeTick_whenNotInGracePeriod_runsStage3WithCurrentPatch() throws InterruptedException {
    given(dailyLeagueEntriesRunner.hasWorkToday()).willReturn(false);
    given(collectMatchIdsRunner.hasPending()).willReturn(false);
    given(patchVersionService.resolveEffectivePatchContext())
        .willReturn(Optional.of(new EffectivePatchContext("15.23", 1000L, null)));
    given(matchIngestRunner.executeWithPriority(anyInt(), any(), any()))
        .willReturn(ingestResult(1));

    runner.executeTick();

    verify(matchIngestRunner).executeWithPriority(props.getIngestBatchSize(), null, "15.23");
  }

  @Test
  @DisplayName("유예 기간 중일 때 Stage 3는 직전 패치(유효 패치 == 직전 패치)로 우선순위를 결정한다")
  void executeTick_whenInGracePeriod_runsStage3WithPreviousPatch() throws InterruptedException {
    given(dailyLeagueEntriesRunner.hasWorkToday()).willReturn(false);
    given(collectMatchIdsRunner.hasPending()).willReturn(false);
    // 유예 기간: endTimeEpochSeconds != null → 유효 패치 = "15.22" (직전 패치)
    given(patchVersionService.resolveEffectivePatchContext())
        .willReturn(Optional.of(new EffectivePatchContext("15.22", 900L, 1000L)));
    given(matchIngestRunner.executeWithPriority(anyInt(), any(), any()))
        .willReturn(ingestResult(1));

    runner.executeTick();

    verify(matchIngestRunner).executeWithPriority(props.getIngestBatchSize(), null, "15.22");
  }

  @Test
  @DisplayName("패치 정보가 없으면 null 패치로 Stage 3를 호출한다")
  void executeTick_whenNoPatch_callsStage3WithNullPatch() throws InterruptedException {
    given(dailyLeagueEntriesRunner.hasWorkToday()).willReturn(false);
    given(collectMatchIdsRunner.hasPending()).willReturn(false);
    given(patchVersionService.resolveEffectivePatchContext()).willReturn(Optional.empty());
    given(matchIngestRunner.executeWithPriority(anyInt(), any(), any()))
        .willReturn(ingestResult(1));

    runner.executeTick();

    verify(matchIngestRunner).executeWithPriority(props.getIngestBatchSize(), null, null);
  }

  @Test
  @DisplayName("stage3PriorityTier가 EMERALD이면 EMERALD 티어로 Stage 3를 호출한다")
  void executeTick_whenStage3PriorityTierIsEmerald_callsStage3WithEmeraldTier()
      throws InterruptedException {
    props.setStage3PriorityTier(Tier.EMERALD);
    given(dailyLeagueEntriesRunner.hasWorkToday()).willReturn(false);
    given(collectMatchIdsRunner.hasPending()).willReturn(false);
    given(patchVersionService.resolveEffectivePatchContext())
        .willReturn(Optional.of(new EffectivePatchContext("15.23", 1000L, null)));
    given(matchIngestRunner.executeWithPriority(anyInt(), any(), any()))
        .willReturn(ingestResult(1));

    runner.executeTick();

    verify(matchIngestRunner).executeWithPriority(props.getIngestBatchSize(), Tier.EMERALD, "15.23");
  }

  @Test
  @DisplayName("Stage 3에서 처리된 항목이 없으면 폴링 간격만큼 대기한다")
  void executeTick_whenNothingInQueue_sleepsPollingInterval() throws InterruptedException {
    given(dailyLeagueEntriesRunner.hasWorkToday()).willReturn(false);
    given(collectMatchIdsRunner.hasPending()).willReturn(false);
    given(patchVersionService.resolveEffectivePatchContext()).willReturn(Optional.empty());
    given(matchIngestRunner.executeWithPriority(anyInt(), any(), any()))
        .willReturn(ingestResult(0));

    long start = System.currentTimeMillis();
    runner.executeTick();
    long elapsed = System.currentTimeMillis() - start;

    // pollingIntervalMs=100 으로 설정했으므로 최소 80ms 이상 sleep 해야 함
    assertThat(elapsed).isGreaterThanOrEqualTo(80L);
  }

  @Test
  @DisplayName("Stage 1에서 429 발생 시 속도 제한 예외가 전파된다")
  void executeTick_whenStage1Throws429_propagates() {
    given(dailyLeagueEntriesRunner.hasWorkToday()).willReturn(true);
    given(dailyLeagueEntriesRunner.runNextChunk()).willThrow(new RiotRateLimitedException("429"));

    assertThatThrownBy(() -> runner.executeTick())
        .isInstanceOf(RiotRateLimitedException.class);
  }

  @Test
  @DisplayName("Stage 3에서 429 발생 시 속도 제한 예외가 전파된다")
  void executeTick_whenStage3Throws429_propagates() {
    given(dailyLeagueEntriesRunner.hasWorkToday()).willReturn(false);
    given(collectMatchIdsRunner.hasPending()).willReturn(false);
    given(patchVersionService.resolveEffectivePatchContext()).willReturn(Optional.empty());
    given(matchIngestRunner.executeWithPriority(anyInt(), any(), any()))
        .willThrow(new RiotRateLimitedException("429"));

    assertThatThrownBy(() -> runner.executeTick())
        .isInstanceOf(RiotRateLimitedException.class);
  }

  @Test
  @DisplayName("priority 티어가 비면 다음 티어로 순회하다가 잡힌 시점에 tick 종료")
  void executeTick_whenPriorityTierEmpty_fallsBackToNextTier() throws InterruptedException {
    props.setStage3PriorityTier(Tier.EMERALD);
    given(dailyLeagueEntriesRunner.hasWorkToday()).willReturn(false);
    given(collectMatchIdsRunner.hasPending()).willReturn(false);
    given(patchVersionService.resolveEffectivePatchContext())
        .willReturn(Optional.of(new EffectivePatchContext("15.23", 1000L, null)));
    // EMERALD: 0건, CHALLENGER: 1건 — 그 뒤 티어는 호출되면 안 됨
    given(matchIngestRunner.executeWithPriority(anyInt(), eq(Tier.EMERALD), any()))
        .willReturn(ingestResult(0));
    given(matchIngestRunner.executeWithPriority(anyInt(), eq(Tier.CHALLENGER), any()))
        .willReturn(ingestResult(1));

    runner.executeTick();

    verify(matchIngestRunner).executeWithPriority(props.getIngestBatchSize(), Tier.EMERALD, "15.23");
    verify(matchIngestRunner).executeWithPriority(props.getIngestBatchSize(), Tier.CHALLENGER, "15.23");
    verify(matchIngestRunner, never())
        .executeWithPriority(anyInt(), eq(Tier.GRANDMASTER), any());
    verify(matchIngestRunner, never())
        .executeWithPriority(anyInt(), eq(Tier.MASTER), any());
  }

  @Test
  @DisplayName("priority 티어 지정 시 모든 티어가 비면 폴링 간격만큼 대기한다")
  void executeTick_whenPriorityTierSetAndAllTiersEmpty_sleepsPollingInterval()
      throws InterruptedException {
    props.setStage3PriorityTier(Tier.EMERALD);
    given(dailyLeagueEntriesRunner.hasWorkToday()).willReturn(false);
    given(collectMatchIdsRunner.hasPending()).willReturn(false);
    given(patchVersionService.resolveEffectivePatchContext()).willReturn(Optional.empty());
    given(matchIngestRunner.executeWithPriority(anyInt(), any(), any()))
        .willReturn(ingestResult(0));

    long start = System.currentTimeMillis();
    runner.executeTick();
    long elapsed = System.currentTimeMillis() - start;

    // CHALLENGER ~ EMERALD까지 5개 티어 시도 후 sleep
    verify(matchIngestRunner, times(5)).executeWithPriority(anyInt(), any(Tier.class), any());
    assertThat(elapsed).isGreaterThanOrEqualTo(80L);
  }

  @Test
  @DisplayName("PLATINUM 이하 티어는 round-robin 순회에서 제외된다")
  void executeTick_whenPriorityTierSetAndAllTiersEmpty_skipsPlatinumAndBelow()
      throws InterruptedException {
    props.setStage3PriorityTier(Tier.EMERALD);
    given(dailyLeagueEntriesRunner.hasWorkToday()).willReturn(false);
    given(collectMatchIdsRunner.hasPending()).willReturn(false);
    given(patchVersionService.resolveEffectivePatchContext()).willReturn(Optional.empty());
    given(matchIngestRunner.executeWithPriority(anyInt(), any(), any()))
        .willReturn(ingestResult(0));

    runner.executeTick();

    verify(matchIngestRunner, never()).executeWithPriority(anyInt(), eq(Tier.PLATINUM), any());
    verify(matchIngestRunner, never()).executeWithPriority(anyInt(), eq(Tier.GOLD), any());
    verify(matchIngestRunner, never()).executeWithPriority(anyInt(), eq(Tier.SILVER), any());
    verify(matchIngestRunner, never()).executeWithPriority(anyInt(), eq(Tier.BRONZE), any());
    verify(matchIngestRunner, never()).executeWithPriority(anyInt(), eq(Tier.IRON), any());
  }

  @Test
  @DisplayName("stage3PriorityTier가 ALL_TIERS이면 단일 호출(레거시)로 null tier를 사용한다")
  void executeTick_whenStage3PriorityTierIsAllTiers_singleCallWithNull()
      throws InterruptedException {
    props.setStage3PriorityTier(Tier.ALL_TIERS);
    given(dailyLeagueEntriesRunner.hasWorkToday()).willReturn(false);
    given(collectMatchIdsRunner.hasPending()).willReturn(false);
    given(patchVersionService.resolveEffectivePatchContext())
        .willReturn(Optional.of(new EffectivePatchContext("15.23", 1000L, null)));
    given(matchIngestRunner.executeWithPriority(anyInt(), any(), any()))
        .willReturn(ingestResult(1));

    runner.executeTick();

    verify(matchIngestRunner, times(1))
        .executeWithPriority(props.getIngestBatchSize(), null, "15.23");
  }

  private MatchIngestRunner.Result ingestResult(int picked) {
    return new MatchIngestRunner.Result(0, picked, picked, picked, 0, 0);
  }
}
