package com.bestduo_BE.pipeline.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.bestduo_BE.common.application.PatchVersionService;
import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.persistence.entity.DailyPipelineState;
import com.bestduo_BE.common.infra.riot.budget.DailyBudgetTracker;
import com.bestduo_BE.config.PipelineProperties;
import com.bestduo_BE.coverage.infra.persistence.entity.CoverageBucket;
import com.bestduo_BE.coverage.infra.persistence.repository.CoverageBucketJpaRepository;
import com.bestduo_BE.seed.application.SeedBootstrapExecutor;
import com.bestduo_BE.seed.application.SeedBootstrapExecutor.SeedBootstrapResult;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DailySeedRunnerTest {

  @Mock
  private SeedBootstrapExecutor seedBootstrapExecutor;

  @Mock
  private CoverageBucketJpaRepository coverageBucketRepository;

  @Mock
  private DailyBudgetTracker budgetTracker;

  @Mock
  private PatchVersionService patchVersionService;

  private PipelineProperties props;
  private DailySeedRunner runner;

  @BeforeEach
  void setUp() {
    props = new PipelineProperties();
    runner = new DailySeedRunner(
        seedBootstrapExecutor, coverageBucketRepository, budgetTracker, patchVersionService, props);
  }

  // ── hasWorkToday ────────────────────────────────────────────────────

  @Test
  @DisplayName("seed 예산 소진 시 hasWorkToday는 false")
  void hasWorkToday_whenBudgetExhausted_returnsFalse() {
    given(budgetTracker.canSeed()).willReturn(false);

    assertThat(runner.hasWorkToday()).isFalse();
  }

  @Test
  @DisplayName("apex 티어가 오늘 완료되지 않았으면 hasWorkToday는 true")
  void hasWorkToday_whenApexTierNotCompleted_returnsTrue() {
    given(budgetTracker.canSeed()).willReturn(true);
    DailyPipelineState state = DailyPipelineState.create(LocalDate.now());
    given(budgetTracker.getOrCreateTodayState()).willReturn(state);

    assertThat(runner.hasWorkToday()).isTrue();
  }

  @Test
  @DisplayName("모든 apex·DIA·EME 완료 시 hasWorkToday는 false")
  void hasWorkToday_whenAllCompleted_returnsFalse() {
    given(budgetTracker.canSeed()).willReturn(true);

    DailyPipelineState state = DailyPipelineState.create(LocalDate.now());
    state.recordSeedCompletedTier("CHALLENGER");
    state.recordSeedCompletedTier("GRANDMASTER");
    state.recordSeedCompletedTier("MASTER");
    given(budgetTracker.getOrCreateTodayState()).willReturn(state);

    CoverageBucket diaBucket = bucketWithDailySeedCompleted(Tier.DIAMOND, "15.23");
    CoverageBucket emeBucket = bucketWithDailySeedCompleted(Tier.EMERALD, "15.23");
    given(patchVersionService.currentPatchVersion()).willReturn(Optional.of("15.23"));
    given(coverageBucketRepository.findByPatchAndTier("15.23", Tier.DIAMOND)).willReturn(Optional.of(diaBucket));
    given(coverageBucketRepository.findByPatchAndTier("15.23", Tier.EMERALD)).willReturn(Optional.of(emeBucket));

    assertThat(runner.hasWorkToday()).isFalse();
  }

  @Test
  @DisplayName("DIA 버킷이 미완료이면 hasWorkToday는 true")
  void hasWorkToday_whenDiaBucketNotCompleted_returnsTrue() {
    given(budgetTracker.canSeed()).willReturn(true);

    DailyPipelineState state = DailyPipelineState.create(LocalDate.now());
    state.recordSeedCompletedTier("CHALLENGER");
    state.recordSeedCompletedTier("GRANDMASTER");
    state.recordSeedCompletedTier("MASTER");
    given(budgetTracker.getOrCreateTodayState()).willReturn(state);

    given(patchVersionService.currentPatchVersion()).willReturn(Optional.of("15.23"));
    CoverageBucket diaIncomplete = bucketNotCompleted(Tier.DIAMOND, "15.23");
    given(coverageBucketRepository.findByPatchAndTier("15.23", Tier.DIAMOND)).willReturn(Optional.of(diaIncomplete));

    assertThat(runner.hasWorkToday()).isTrue();
  }

  // ── runNextChunk ────────────────────────────────────────────────────

  @Test
  @DisplayName("seed 예산 소진 시 runNextChunk는 BUDGET_EXHAUSTED 반환")
  void runNextChunk_whenBudgetExhausted_returnsBudgetExhausted() {
    given(budgetTracker.canSeed()).willReturn(false);

    DailySeedRunner.ChunkResult result = runner.runNextChunk();

    assertThat(result.type()).isEqualTo(DailySeedRunner.ChunkResult.Type.BUDGET_EXHAUSTED);
    verify(seedBootstrapExecutor, never()).execute(any());
  }

  @Test
  @DisplayName("CHALLENGER 티어가 미완료면 SeedBootstrapExecutor를 호출하고 완료 기록")
  void runNextChunk_whenChallengerNotDone_executesSeedAndRecordsCompletion() {
    given(budgetTracker.canSeed()).willReturn(true);

    DailyPipelineState state = DailyPipelineState.create(LocalDate.now());
    given(budgetTracker.getOrCreateTodayState()).willReturn(state);

    SeedBootstrapResult result = new SeedBootstrapResult(1, 5, 5, 0, 0);
    given(seedBootstrapExecutor.execute(argThat(cmd ->
        "CHALLENGER".equals(cmd.tier()) && cmd.startPage() == 1
    ))).willReturn(result);

    DailySeedRunner.ChunkResult chunk = runner.runNextChunk();

    assertThat(chunk.type()).isEqualTo(DailySeedRunner.ChunkResult.Type.APEX_TIER_CHUNK);
    assertThat(chunk.tier()).isEqualTo(Tier.CHALLENGER);
    assertThat(state.getSeedCompletedTiers()).contains("\"CHALLENGER\"");
    verify(budgetTracker).recordSeedCall(1);
  }

  @Test
  @DisplayName("apex 티어 모두 완료 후 DIA 버킷 미완료면 DIA 페이지 실행")
  void runNextChunk_whenApexDoneAndDiaPending_executesDiaPage() {
    given(budgetTracker.canSeed()).willReturn(true);

    DailyPipelineState state = DailyPipelineState.create(LocalDate.now());
    state.recordSeedCompletedTier("CHALLENGER");
    state.recordSeedCompletedTier("GRANDMASTER");
    state.recordSeedCompletedTier("MASTER");
    given(budgetTracker.getOrCreateTodayState()).willReturn(state);

    given(patchVersionService.currentPatchVersion()).willReturn(Optional.of("15.23"));

    CoverageBucket diaBucket = bucketNotCompleted(Tier.DIAMOND, "15.23");
    given(coverageBucketRepository.findByPatchAndTier("15.23", Tier.DIAMOND)).willReturn(Optional.of(diaBucket));

    SeedBootstrapResult seedResult = new SeedBootstrapResult(1, 10, 3, 0, 0);
    given(seedBootstrapExecutor.execute(argThat(cmd ->
        "DIAMOND".equals(cmd.tier()) && cmd.startPage() == 1
    ))).willReturn(seedResult);

    DailySeedRunner.ChunkResult chunk = runner.runNextChunk();

    assertThat(chunk.type()).isEqualTo(DailySeedRunner.ChunkResult.Type.DIA_EME_PAGE);
    assertThat(chunk.tier()).isEqualTo(Tier.DIAMOND);
    verify(budgetTracker).recordSeedCall(1);
  }

  @Test
  @DisplayName("DIA 페이지가 빈 응답이면 해당 버킷을 dailySeedCompleted로 표시")
  void runNextChunk_whenDiaPageEmpty_marksBucketCompleted() {
    given(budgetTracker.canSeed()).willReturn(true);

    DailyPipelineState state = DailyPipelineState.create(LocalDate.now());
    state.recordSeedCompletedTier("CHALLENGER");
    state.recordSeedCompletedTier("GRANDMASTER");
    state.recordSeedCompletedTier("MASTER");
    given(budgetTracker.getOrCreateTodayState()).willReturn(state);

    given(patchVersionService.currentPatchVersion()).willReturn(Optional.of("15.23"));

    CoverageBucket diaBucket = bucketNotCompleted(Tier.DIAMOND, "15.23");
    given(coverageBucketRepository.findByPatchAndTier("15.23", Tier.DIAMOND)).willReturn(Optional.of(diaBucket));
    given(coverageBucketRepository.save(any(CoverageBucket.class))).willReturn(diaBucket);

    // 빈 응답 (entriesFetched = 0)
    SeedBootstrapResult emptyResult = new SeedBootstrapResult(1, 0, 0, 0, 0);
    given(seedBootstrapExecutor.execute(any())).willReturn(emptyResult);

    runner.runNextChunk();

    assertThat(diaBucket.isDailySeedCompleted()).isTrue();
    verify(coverageBucketRepository).save(diaBucket);
  }

  @Test
  @DisplayName("모든 티어 완료 시 runNextChunk는 NO_WORK 반환")
  void runNextChunk_whenAllCompleted_returnsNoWork() {
    given(budgetTracker.canSeed()).willReturn(true);

    DailyPipelineState state = DailyPipelineState.create(LocalDate.now());
    state.recordSeedCompletedTier("CHALLENGER");
    state.recordSeedCompletedTier("GRANDMASTER");
    state.recordSeedCompletedTier("MASTER");
    given(budgetTracker.getOrCreateTodayState()).willReturn(state);

    given(patchVersionService.currentPatchVersion()).willReturn(Optional.of("15.23"));
    given(coverageBucketRepository.findByPatchAndTier("15.23", Tier.DIAMOND))
        .willReturn(Optional.of(bucketWithDailySeedCompleted(Tier.DIAMOND, "15.23")));
    given(coverageBucketRepository.findByPatchAndTier("15.23", Tier.EMERALD))
        .willReturn(Optional.of(bucketWithDailySeedCompleted(Tier.EMERALD, "15.23")));

    DailySeedRunner.ChunkResult result = runner.runNextChunk();

    assertThat(result.type()).isEqualTo(DailySeedRunner.ChunkResult.Type.NO_WORK);
  }

  // ── helpers ────────────────────────────────────────────────────────

  private CoverageBucket bucketNotCompleted(Tier tier, String patch) {
    return CoverageBucket.create(patch, tier, 1000L, 1);
  }

  private CoverageBucket bucketWithDailySeedCompleted(Tier tier, String patch) {
    CoverageBucket bucket = CoverageBucket.create(patch, tier, 1000L, 1);
    bucket.markDailySeedCompleted();
    return bucket;
  }
}
