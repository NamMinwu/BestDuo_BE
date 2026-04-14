package com.bestduo_BE.pipeline.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.bestduo_BE.common.application.PatchVersionService;
import com.bestduo_BE.common.infra.riot.exception.RiotRateLimitedException;
import com.bestduo_BE.config.PipelineProperties;
import com.bestduo_BE.ingest.application.MatchIngestWorker;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PipelineRunnerTest {

  @Mock private DailySeedRunner dailySeedRunner;
  @Mock private CollectMatchIdsRunner collectMatchIdsRunner;
  @Mock private MatchIngestWorker matchIngestWorker;
  @Mock private PatchVersionService patchVersionService;

  private PipelineProperties props;
  private PipelineRunner runner;

  @BeforeEach
  void setUp() {
    props = new PipelineProperties();
    props.setIngestBatchSize(10);
    props.setPollingIntervalMs(100);
    runner = new PipelineRunner(dailySeedRunner, collectMatchIdsRunner, matchIngestWorker,
        patchVersionService, props);
  }

  @Test
  @DisplayName("Stage 1 작업이 있으면 runNextChunk만 호출된다")
  void executeTick_whenStage1HasWork_runsOnlyStage1() throws InterruptedException {
    given(dailySeedRunner.hasWorkToday()).willReturn(true);

    runner.executeTick();

    verify(dailySeedRunner).runNextChunk();
    verify(collectMatchIdsRunner, never()).runBatch();
    verify(matchIngestWorker, never()).executeWithPriority(anyInt(), any());
  }

  @Test
  @DisplayName("Stage 1 없고 Stage 2 pending이면 runBatch만 호출된다")
  void executeTick_whenStage2HasPending_runsOnlyStage2() throws InterruptedException {
    given(dailySeedRunner.hasWorkToday()).willReturn(false);
    given(collectMatchIdsRunner.hasPending()).willReturn(true);

    runner.executeTick();

    verify(collectMatchIdsRunner).runBatch();
    verify(matchIngestWorker, never()).executeWithPriority(anyInt(), any());
  }

  @Test
  @DisplayName("Stage 1·2 없으면 currentPatch와 함께 Stage 3 executeWithPriority를 호출한다")
  void executeTick_whenNoStage1Or2_runsStage3WithCurrentPatch() throws InterruptedException {
    given(dailySeedRunner.hasWorkToday()).willReturn(false);
    given(collectMatchIdsRunner.hasPending()).willReturn(false);
    given(patchVersionService.currentPatchVersion()).willReturn(Optional.of("15.23"));
    given(matchIngestWorker.executeWithPriority(anyInt(), any()))
        .willReturn(ingestResult(1));

    runner.executeTick();

    verify(matchIngestWorker).executeWithPriority(props.getIngestBatchSize(), "15.23");
  }

  @Test
  @DisplayName("patch 정보가 없으면 null로 Stage 3를 호출한다")
  void executeTick_whenNoPatch_callsStage3WithNullPatch() throws InterruptedException {
    given(dailySeedRunner.hasWorkToday()).willReturn(false);
    given(collectMatchIdsRunner.hasPending()).willReturn(false);
    given(patchVersionService.currentPatchVersion()).willReturn(Optional.empty());
    given(matchIngestWorker.executeWithPriority(anyInt(), any()))
        .willReturn(ingestResult(1));

    runner.executeTick();

    verify(matchIngestWorker).executeWithPriority(props.getIngestBatchSize(), null);
  }

  @Test
  @DisplayName("Stage 3에서 picked == 0이면 pollingIntervalMs 동안 sleep한다")
  void executeTick_whenNothingInQueue_sleepsPollingInterval() throws InterruptedException {
    given(dailySeedRunner.hasWorkToday()).willReturn(false);
    given(collectMatchIdsRunner.hasPending()).willReturn(false);
    given(patchVersionService.currentPatchVersion()).willReturn(Optional.empty());
    given(matchIngestWorker.executeWithPriority(anyInt(), any()))
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
    given(dailySeedRunner.hasWorkToday()).willReturn(true);
    given(dailySeedRunner.runNextChunk()).willThrow(new RiotRateLimitedException("429"));

    assertThatThrownBy(() -> runner.executeTick())
        .isInstanceOf(RiotRateLimitedException.class);
  }

  @Test
  @DisplayName("Stage 3에서 429 발생 시 RiotRateLimitedException이 전파된다")
  void executeTick_whenStage3Throws429_propagates() {
    given(dailySeedRunner.hasWorkToday()).willReturn(false);
    given(collectMatchIdsRunner.hasPending()).willReturn(false);
    given(patchVersionService.currentPatchVersion()).willReturn(Optional.empty());
    given(matchIngestWorker.executeWithPriority(anyInt(), any()))
        .willThrow(new RiotRateLimitedException("429"));

    assertThatThrownBy(() -> runner.executeTick())
        .isInstanceOf(RiotRateLimitedException.class);
  }

  // ── helpers ─────────────────────────────────────────────────────────────

  private MatchIngestWorker.Result ingestResult(int picked) {
    return new MatchIngestWorker.Result(0, picked, picked, picked, 0, 0);
  }
}
