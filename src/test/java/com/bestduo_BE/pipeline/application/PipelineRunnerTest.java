package com.bestduo_BE.pipeline.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.bestduo_BE.common.application.PatchVersionService;
import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.riot.exception.RiotRateLimitedException;
import com.bestduo_BE.config.PipelineProperties;
import com.bestduo_BE.ingest.application.MatchIngestRunner;
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
    runner = new PipelineRunner(dailyLeagueEntriesRunner, collectMatchIdsRunner, matchIngestRunner,
        patchVersionService, props);
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
  @DisplayName("Stage 1 없고 Stage 2 pending이면 runBatch만 호출된다")
  void executeTick_whenStage2HasPending_runsOnlyStage2() throws InterruptedException {
    given(dailyLeagueEntriesRunner.hasWorkToday()).willReturn(false);
    given(collectMatchIdsRunner.hasPending()).willReturn(true);

    runner.executeTick();

    verify(collectMatchIdsRunner).runBatch();
    verify(matchIngestRunner, never()).executeWithPriority(anyInt(), any(), any());
  }

  @Test
  @DisplayName("Stage 1·2 없으면 ALL_TIERS + currentPatch로 Stage 3 executeWithPriority를 호출한다")
  void executeTick_whenNoStage1Or2_runsStage3WithCurrentPatch() throws InterruptedException {
    given(dailyLeagueEntriesRunner.hasWorkToday()).willReturn(false);
    given(collectMatchIdsRunner.hasPending()).willReturn(false);
    given(patchVersionService.currentPatchVersion()).willReturn(Optional.of("15.23"));
    given(matchIngestRunner.executeWithPriority(anyInt(), any(), any()))
        .willReturn(ingestResult(1));

    runner.executeTick();

    verify(matchIngestRunner).executeWithPriority(props.getIngestBatchSize(), Tier.ALL_TIERS, "15.23");
  }

  @Test
  @DisplayName("patch 정보가 없으면 ALL_TIERS + null patch로 Stage 3를 호출한다")
  void executeTick_whenNoPatch_callsStage3WithNullPatch() throws InterruptedException {
    given(dailyLeagueEntriesRunner.hasWorkToday()).willReturn(false);
    given(collectMatchIdsRunner.hasPending()).willReturn(false);
    given(patchVersionService.currentPatchVersion()).willReturn(Optional.empty());
    given(matchIngestRunner.executeWithPriority(anyInt(), any(), any()))
        .willReturn(ingestResult(1));

    runner.executeTick();

    verify(matchIngestRunner).executeWithPriority(props.getIngestBatchSize(), Tier.ALL_TIERS, null);
  }

  @Test
  @DisplayName("stage3PriorityTier가 EMERALD이면 EMERALD tier로 Stage 3를 호출한다")
  void executeTick_whenStage3PriorityTierIsEmerald_callsStage3WithEmeraldTier()
      throws InterruptedException {
    props.setStage3PriorityTier(Tier.EMERALD);
    given(dailyLeagueEntriesRunner.hasWorkToday()).willReturn(false);
    given(collectMatchIdsRunner.hasPending()).willReturn(false);
    given(patchVersionService.currentPatchVersion()).willReturn(Optional.of("15.23"));
    given(matchIngestRunner.executeWithPriority(anyInt(), any(), any()))
        .willReturn(ingestResult(1));

    runner.executeTick();

    verify(matchIngestRunner).executeWithPriority(props.getIngestBatchSize(), Tier.EMERALD, "15.23");
  }

  @Test
  @DisplayName("Stage 3에서 picked == 0이면 pollingIntervalMs 동안 sleep한다")
  void executeTick_whenNothingInQueue_sleepsPollingInterval() throws InterruptedException {
    given(dailyLeagueEntriesRunner.hasWorkToday()).willReturn(false);
    given(collectMatchIdsRunner.hasPending()).willReturn(false);
    given(patchVersionService.currentPatchVersion()).willReturn(Optional.empty());
    given(matchIngestRunner.executeWithPriority(anyInt(), any(), any()))
        .willReturn(ingestResult(0));

    long start = System.currentTimeMillis();
    runner.executeTick();
    long elapsed = System.currentTimeMillis() - start;

    // pollingIntervalMs=100 으로 설정했으므로 최소 80ms 이상 sleep 해야 함
    org.assertj.core.api.Assertions.assertThat(elapsed).isGreaterThanOrEqualTo(80L);
  }

  @Test
  @DisplayName("Stage 1에서 429 발생 시 RiotRateLimitedException이 전파된다")
  void executeTick_whenStage1Throws429_propagates() {
    given(dailyLeagueEntriesRunner.hasWorkToday()).willReturn(true);
    given(dailyLeagueEntriesRunner.runNextChunk()).willThrow(new RiotRateLimitedException("429"));

    assertThatThrownBy(() -> runner.executeTick())
        .isInstanceOf(RiotRateLimitedException.class);
  }

  @Test
  @DisplayName("Stage 3에서 429 발생 시 RiotRateLimitedException이 전파된다")
  void executeTick_whenStage3Throws429_propagates() {
    given(dailyLeagueEntriesRunner.hasWorkToday()).willReturn(false);
    given(collectMatchIdsRunner.hasPending()).willReturn(false);
    given(patchVersionService.currentPatchVersion()).willReturn(Optional.empty());
    given(matchIngestRunner.executeWithPriority(anyInt(), any(), any()))
        .willThrow(new RiotRateLimitedException("429"));

    assertThatThrownBy(() -> runner.executeTick())
        .isInstanceOf(RiotRateLimitedException.class);
  }

  // ── helpers ─────────────────────────────────────────────────────────────

  private MatchIngestRunner.Result ingestResult(int picked) {
    return new MatchIngestRunner.Result(0, picked, picked, picked, 0, 0);
  }
}
