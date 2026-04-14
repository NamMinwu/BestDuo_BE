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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

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

  @Test
  @DisplayName("DIA 버킷이 없어도 auto-create 대상이면 hasWorkToday는 true")
  void hasWorkToday_whenDiaBucketMissing_returnsTrue() {
    given(budgetTracker.canSeed()).willReturn(true);

    DailyPipelineState state = DailyPipelineState.create(LocalDate.now());
    state.recordSeedCompletedTier("CHALLENGER");
    state.recordSeedCompletedTier("GRANDMASTER");
    state.recordSeedCompletedTier("MASTER");
    given(budgetTracker.getOrCreateTodayState()).willReturn(state);

    given(patchVersionService.currentPatchVersion()).willReturn(Optional.of("15.23"));
    given(coverageBucketRepository.findByPatchAndTier("15.23", Tier.DIAMOND)).willReturn(Optional.empty());

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
    verify(budgetTracker).recordSeedCall(1, "CHALLENGER");
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
  @DisplayName("DIA 버킷이 없으면 auto-create 후 첫 페이지를 실행한다")
  void runNextChunk_whenDiaBucketMissing_autoCreatesAndExecutesDiaPage() {
    given(budgetTracker.canSeed()).willReturn(true);

    DailyPipelineState state = DailyPipelineState.create(LocalDate.now());
    state.recordSeedCompletedTier("CHALLENGER");
    state.recordSeedCompletedTier("GRANDMASTER");
    state.recordSeedCompletedTier("MASTER");
    given(budgetTracker.getOrCreateTodayState()).willReturn(state);

    given(patchVersionService.currentPatchVersion()).willReturn(Optional.of("15.23"));

    given(coverageBucketRepository.findByPatchAndTier("15.23", Tier.DIAMOND)).willReturn(Optional.empty());
    given(coverageBucketRepository.save(any(CoverageBucket.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    SeedBootstrapResult seedResult = new SeedBootstrapResult(1, 10, 3, 0, 0);
    given(seedBootstrapExecutor.execute(argThat(cmd ->
        "DIAMOND".equals(cmd.tier())
            && "I".equals(cmd.division())
            && cmd.startPage() == 1
    ))).willReturn(seedResult);

    DailySeedRunner.ChunkResult chunk = runner.runNextChunk();

    assertThat(chunk.type()).isEqualTo(DailySeedRunner.ChunkResult.Type.DIA_EME_PAGE);
    verify(coverageBucketRepository, org.mockito.Mockito.atLeastOnce()).save(argThat(bucket ->
        bucket.getTier() == Tier.DIAMOND
            && "15.23".equals(bucket.getPatch())
            && bucket.getTargetMatchCount() == props.getDiaEmeCoverageTarget()
    ));
  }

  @Test
  @DisplayName("auto-create 저장이 unique 충돌이면 기존 버킷을 다시 읽어 실행한다")
  void runNextChunk_whenAutoCreateConflicts_reloadsExistingBucket() {
    given(budgetTracker.canSeed()).willReturn(true);

    DailyPipelineState state = DailyPipelineState.create(LocalDate.now());
    state.recordSeedCompletedTier("CHALLENGER");
    state.recordSeedCompletedTier("GRANDMASTER");
    state.recordSeedCompletedTier("MASTER");
    given(budgetTracker.getOrCreateTodayState()).willReturn(state);

    given(patchVersionService.currentPatchVersion()).willReturn(Optional.of("15.23"));

    CoverageBucket existingBucket = customBucket(Tier.DIAMOND, "15.23", "III", 7, 0, false, null);
    given(coverageBucketRepository.findByPatchAndTier("15.23", Tier.DIAMOND))
        .willReturn(Optional.empty())
        .willReturn(Optional.of(existingBucket));
    given(coverageBucketRepository.save(any(CoverageBucket.class)))
        .willThrow(new DataIntegrityViolationException("duplicate key"))
        .willReturn(existingBucket);

    SeedBootstrapResult seedResult = new SeedBootstrapResult(1, 5, 2, 0, 0);
    given(seedBootstrapExecutor.execute(argThat(cmd ->
        "DIAMOND".equals(cmd.tier())
            && "III".equals(cmd.division())
            && cmd.startPage() == 7
    ))).willReturn(seedResult);

    DailySeedRunner.ChunkResult chunk = runner.runNextChunk();

    assertThat(chunk.type()).isEqualTo(DailySeedRunner.ChunkResult.Type.DIA_EME_PAGE);
    assertThat(existingBucket.getSeedDivision()).isEqualTo("III");
    assertThat(existingBucket.getSeedPage()).isEqualTo(8);
  }

  @Test
  @DisplayName("DIA 페이지가 빈 응답이면 완료 대신 다음 division 시작점으로 이동한다")
  void runNextChunk_whenDiaPageEmpty_rollsToNextDivisionStart() {
    given(budgetTracker.canSeed()).willReturn(true);

    DailyPipelineState state = DailyPipelineState.create(LocalDate.now());
    state.recordSeedCompletedTier("CHALLENGER");
    state.recordSeedCompletedTier("GRANDMASTER");
    state.recordSeedCompletedTier("MASTER");
    given(budgetTracker.getOrCreateTodayState()).willReturn(state);

    given(patchVersionService.currentPatchVersion()).willReturn(Optional.of("15.23"));

    CoverageBucket diaBucket = customBucket(Tier.DIAMOND, "15.23", "I", 99, 0, false, OffsetDateTime.now());
    given(coverageBucketRepository.findByPatchAndTier("15.23", Tier.DIAMOND)).willReturn(Optional.of(diaBucket));
    given(coverageBucketRepository.save(any(CoverageBucket.class))).willReturn(diaBucket);

    SeedBootstrapResult emptyResult = new SeedBootstrapResult(1, 0, 0, 0, 0);
    given(seedBootstrapExecutor.execute(any())).willReturn(emptyResult);

    runner.runNextChunk();

    assertThat(diaBucket.isDailySeedCompleted()).isFalse();
    assertThat(diaBucket.getSeedDivision()).isEqualTo("II");
    assertThat(diaBucket.getSeedPage()).isEqualTo(1);
    assertThat(diaBucket.getDailySeedStepsProcessed()).isEqualTo(1);
    verify(coverageBucketRepository).save(diaBucket);
  }

  @Test
  @DisplayName("DIA 페이지가 정상 응답이면 같은 division의 다음 페이지로 이동한다")
  void runNextChunk_whenDiaPageHasEntries_advancesToNextPage() {
    given(budgetTracker.canSeed()).willReturn(true);

    DailyPipelineState state = DailyPipelineState.create(LocalDate.now());
    state.recordSeedCompletedTier("CHALLENGER");
    state.recordSeedCompletedTier("GRANDMASTER");
    state.recordSeedCompletedTier("MASTER");
    given(budgetTracker.getOrCreateTodayState()).willReturn(state);

    given(patchVersionService.currentPatchVersion()).willReturn(Optional.of("15.23"));

    CoverageBucket diaBucket = customBucket(Tier.DIAMOND, "15.23", "II", 4, 0, false, OffsetDateTime.now());
    given(coverageBucketRepository.findByPatchAndTier("15.23", Tier.DIAMOND)).willReturn(Optional.of(diaBucket));
    given(coverageBucketRepository.save(any(CoverageBucket.class))).willReturn(diaBucket);

    SeedBootstrapResult seedResult = new SeedBootstrapResult(1, 10, 3, 0, 0);
    given(seedBootstrapExecutor.execute(any())).willReturn(seedResult);

    runner.runNextChunk();

    assertThat(diaBucket.isDailySeedCompleted()).isFalse();
    assertThat(diaBucket.getSeedDivision()).isEqualTo("II");
    assertThat(diaBucket.getSeedPage()).isEqualTo(5);
    assertThat(diaBucket.getDailySeedStepsProcessed()).isEqualTo(1);
  }

  @Test
  @DisplayName("DIA 페이지 실행 후 일일 quota를 모두 쓰면 완료 처리한다")
  void runNextChunk_whenDailyQuotaReached_marksBucketCompleted() {
    props.setDiaEmeDailyPageQuota(10);
    given(budgetTracker.canSeed()).willReturn(true);

    DailyPipelineState state = DailyPipelineState.create(LocalDate.now());
    state.recordSeedCompletedTier("CHALLENGER");
    state.recordSeedCompletedTier("GRANDMASTER");
    state.recordSeedCompletedTier("MASTER");
    given(budgetTracker.getOrCreateTodayState()).willReturn(state);

    given(patchVersionService.currentPatchVersion()).willReturn(Optional.of("15.23"));

    CoverageBucket diaBucket = customBucket(Tier.DIAMOND, "15.23", "IV", 8, 9, false, OffsetDateTime.now());
    given(coverageBucketRepository.findByPatchAndTier("15.23", Tier.DIAMOND)).willReturn(Optional.of(diaBucket));
    given(coverageBucketRepository.save(any(CoverageBucket.class))).willReturn(diaBucket);

    SeedBootstrapResult seedResult = new SeedBootstrapResult(1, 3, 1, 0, 0);
    given(seedBootstrapExecutor.execute(any())).willReturn(seedResult);

    runner.runNextChunk();

    assertThat(diaBucket.getDailySeedStepsProcessed()).isEqualTo(10);
    assertThat(diaBucket.isDailySeedCompleted()).isTrue();
  }

  @Test
  @DisplayName("다음 날에는 quota만 reset되고 cursor는 유지된 채 실행된다")
  void runNextChunk_whenNewDay_resetsQuotaButPreservesCursor() {
    props.setDiaEmeDailyPageQuota(10);
    given(budgetTracker.canSeed()).willReturn(true);

    DailyPipelineState state = DailyPipelineState.create(LocalDate.now());
    state.recordSeedCompletedTier("CHALLENGER");
    state.recordSeedCompletedTier("GRANDMASTER");
    state.recordSeedCompletedTier("MASTER");
    given(budgetTracker.getOrCreateTodayState()).willReturn(state);

    given(patchVersionService.currentPatchVersion()).willReturn(Optional.of("15.23"));

    CoverageBucket diaBucket = customBucket(
        Tier.DIAMOND,
        "15.23",
        "III",
        7,
        10,
        true,
        OffsetDateTime.now().minusDays(1));
    given(coverageBucketRepository.findByPatchAndTier("15.23", Tier.DIAMOND)).willReturn(Optional.of(diaBucket));
    given(coverageBucketRepository.save(any(CoverageBucket.class))).willReturn(diaBucket);

    SeedBootstrapResult seedResult = new SeedBootstrapResult(1, 6, 2, 0, 0);
    given(seedBootstrapExecutor.execute(argThat(cmd ->
        "III".equals(cmd.division()) && cmd.startPage() == 7
    ))).willReturn(seedResult);

    runner.runNextChunk();

    assertThat(diaBucket.getDailySeedStepsProcessed()).isEqualTo(1);
    assertThat(diaBucket.isDailySeedCompleted()).isFalse();
    assertThat(diaBucket.getSeedDivision()).isEqualTo("III");
    assertThat(diaBucket.getSeedPage()).isEqualTo(8);
  }

  @Test
  @DisplayName("safety cap에 도달하면 응답이 있어도 다음 division 시작점으로 이동한다")
  void runNextChunk_whenSafetyCapReached_rollsToNextDivisionStart() {
    props.setDiaEmeSafetyMaxPage(100);
    given(budgetTracker.canSeed()).willReturn(true);

    DailyPipelineState state = DailyPipelineState.create(LocalDate.now());
    state.recordSeedCompletedTier("CHALLENGER");
    state.recordSeedCompletedTier("GRANDMASTER");
    state.recordSeedCompletedTier("MASTER");
    given(budgetTracker.getOrCreateTodayState()).willReturn(state);

    given(patchVersionService.currentPatchVersion()).willReturn(Optional.of("15.23"));

    CoverageBucket diaBucket = customBucket(Tier.DIAMOND, "15.23", "IV", 100, 0, false, OffsetDateTime.now());
    given(coverageBucketRepository.findByPatchAndTier("15.23", Tier.DIAMOND)).willReturn(Optional.of(diaBucket));
    given(coverageBucketRepository.save(any(CoverageBucket.class))).willReturn(diaBucket);

    SeedBootstrapResult seedResult = new SeedBootstrapResult(1, 8, 3, 0, 0);
    given(seedBootstrapExecutor.execute(any())).willReturn(seedResult);

    runner.runNextChunk();

    assertThat(diaBucket.getSeedDivision()).isEqualTo("I");
    assertThat(diaBucket.getSeedPage()).isEqualTo(1);
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

  @Test
  @DisplayName("currentPatch가 null이면 DIA/EME seed를 스킵하고 NO_WORK 반환")
  void runNextChunk_whenCurrentPatchNull_skipsDiaEmeAndReturnsNoWork() {
    given(budgetTracker.canSeed()).willReturn(true);

    DailyPipelineState state = DailyPipelineState.create(LocalDate.now());
    state.recordSeedCompletedTier("CHALLENGER");
    state.recordSeedCompletedTier("GRANDMASTER");
    state.recordSeedCompletedTier("MASTER");
    given(budgetTracker.getOrCreateTodayState()).willReturn(state);
    given(patchVersionService.currentPatchVersion()).willReturn(Optional.empty());

    DailySeedRunner.ChunkResult result = runner.runNextChunk();

    assertThat(result.type()).isEqualTo(DailySeedRunner.ChunkResult.Type.NO_WORK);
    verify(seedBootstrapExecutor, never()).execute(any());
    verify(coverageBucketRepository, never()).findByPatchAndTier(any(), any());
  }

  // ── helpers ────────────────────────────────────────────────────────

  private CoverageBucket bucketNotCompleted(Tier tier, String patch) {
    return CoverageBucket.create(patch, tier, 1000L, 1);
  }

  private CoverageBucket bucketWithDailySeedCompleted(Tier tier, String patch) {
    return customBucket(
        tier,
        patch,
        "I",
        1,
        props.getDiaEmeDailyPageQuota(),
        true,
        OffsetDateTime.now());
  }

  private CoverageBucket customBucket(
      Tier tier,
      String patch,
      String division,
      int page,
      int dailySeedStepsProcessed,
      boolean dailySeedCompleted,
      OffsetDateTime dailySeedResetAt) {
    OffsetDateTime now = OffsetDateTime.now();
    return CoverageBucket.builder()
        .patch(patch)
        .tier(tier)
        .targetMatchCount(1000L)
        .currentMatchCount(0L)
        .status(com.bestduo_BE.coverage.domain.model.CoverageBucketStatus.COLLECTING)
        .priority(1)
        .seedDivision(division)
        .seedPage(page)
        .dailySeedStepsProcessed(dailySeedStepsProcessed)
        .dailySeedCompleted(dailySeedCompleted)
        .dailySeedResetAt(dailySeedResetAt)
        .lastEvaluatedAt(now)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }
}
