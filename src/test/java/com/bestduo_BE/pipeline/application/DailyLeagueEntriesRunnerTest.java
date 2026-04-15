package com.bestduo_BE.pipeline.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.bestduo_BE.common.application.PatchVersionService;
import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.persistence.entity.DailyPipelineState;
import com.bestduo_BE.common.infra.riot.budget.DailyBudgetTracker;
import com.bestduo_BE.config.PipelineProperties;
import com.bestduo_BE.coverage.infra.persistence.entity.CoverageBucket;
import com.bestduo_BE.coverage.infra.persistence.repository.CoverageBucketJpaRepository;
import com.bestduo_BE.leagueentry.application.LeagueEntriesFetcher;
import com.bestduo_BE.leagueentry.application.LeagueEntriesFetcher.LeagueEntriesFetchResult;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DailyLeagueEntriesRunnerTest {

  @Mock
  private LeagueEntriesFetcher leagueEntriesFetcher;

  @Mock
  private CoverageBucketJpaRepository coverageBucketRepository;

  @Mock
  private DailyBudgetTracker budgetTracker;

  @Mock
  private PatchVersionService patchVersionService;

  private PipelineProperties props;
  private DailyLeagueEntriesRunner runner;

  @BeforeEach
  void setUp() {
    props = new PipelineProperties();
    runner = new DailyLeagueEntriesRunner(
        leagueEntriesFetcher, coverageBucketRepository, budgetTracker, patchVersionService, props);
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
  @DisplayName("모든 apex·DIA·EME quota 소진 시 hasWorkToday는 false")
  void hasWorkToday_whenAllQuotaExhausted_returnsFalse() {
    given(budgetTracker.canSeed()).willReturn(true);

    DailyPipelineState state = DailyPipelineState.create(LocalDate.now());
    state.recordSeedCompletedTier("CHALLENGER");
    state.recordSeedCompletedTier("GRANDMASTER");
    state.recordSeedCompletedTier("MASTER");
    given(budgetTracker.getOrCreateTodayState()).willReturn(state);

    CoverageBucket diaBucket = bucketWithQuotaExhausted(Tier.DIAMOND, "15.23");
    CoverageBucket emeBucket = bucketWithQuotaExhausted(Tier.EMERALD, "15.23");
    given(patchVersionService.currentPatchVersion()).willReturn(Optional.of("15.23"));
    given(coverageBucketRepository.findByPatchAndTier("15.23", Tier.DIAMOND)).willReturn(Optional.of(diaBucket));
    given(coverageBucketRepository.findByPatchAndTier("15.23", Tier.EMERALD)).willReturn(Optional.of(emeBucket));

    assertThat(runner.hasWorkToday()).isFalse();
  }

  @Test
  @DisplayName("DIA 버킷의 quota가 남아있으면 hasWorkToday는 true")
  void hasWorkToday_whenDiaBucketHasRemainingQuota_returnsTrue() {
    given(budgetTracker.canSeed()).willReturn(true);

    DailyPipelineState state = DailyPipelineState.create(LocalDate.now());
    state.recordSeedCompletedTier("CHALLENGER");
    state.recordSeedCompletedTier("GRANDMASTER");
    state.recordSeedCompletedTier("MASTER");
    given(budgetTracker.getOrCreateTodayState()).willReturn(state);

    given(patchVersionService.currentPatchVersion()).willReturn(Optional.of("15.23"));
    // fresh bucket: dailyPagesProcessed=0 < quota=10
    CoverageBucket diaFresh = bucketNotCompleted(Tier.DIAMOND, "15.23");
    given(coverageBucketRepository.findByPatchAndTier("15.23", Tier.DIAMOND)).willReturn(Optional.of(diaFresh));

    assertThat(runner.hasWorkToday()).isTrue();
  }

  // ── runNextChunk ────────────────────────────────────────────────────

  @Test
  @DisplayName("seed 예산 소진 시 runNextChunk는 BUDGET_EXHAUSTED 반환")
  void runNextChunk_whenBudgetExhausted_returnsBudgetExhausted() {
    given(budgetTracker.canSeed()).willReturn(false);

    DailyLeagueEntriesRunner.ChunkResult result = runner.runNextChunk();

    assertThat(result.type()).isEqualTo(DailyLeagueEntriesRunner.ChunkResult.Type.BUDGET_EXHAUSTED);
    verify(leagueEntriesFetcher, never()).execute(any());
  }

  @Test
  @DisplayName("CHALLENGER 티어가 미완료면 LeagueEntriesFetcher를 호출하고 완료 기록")
  void runNextChunk_whenChallengerNotDone_executesSeedAndRecordsCompletion() {
    given(budgetTracker.canSeed()).willReturn(true);

    DailyPipelineState state = DailyPipelineState.create(LocalDate.now());
    given(budgetTracker.getOrCreateTodayState()).willReturn(state);

    LeagueEntriesFetchResult result = new LeagueEntriesFetchResult(1, 5, 5);
    given(leagueEntriesFetcher.execute(argThat(cmd ->
        "CHALLENGER".equals(cmd.tier()) && cmd.page() == 1
    ))).willReturn(result);

    DailyLeagueEntriesRunner.ChunkResult chunk = runner.runNextChunk();

    assertThat(chunk.type()).isEqualTo(DailyLeagueEntriesRunner.ChunkResult.Type.APEX_TIER_CHUNK);
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

    LeagueEntriesFetchResult seedResult = new LeagueEntriesFetchResult(1, 10, 3);
    given(leagueEntriesFetcher.execute(argThat(cmd ->
        "DIAMOND".equals(cmd.tier()) && cmd.page() == 1
    ))).willReturn(seedResult);

    DailyLeagueEntriesRunner.ChunkResult chunk = runner.runNextChunk();

    assertThat(chunk.type()).isEqualTo(DailyLeagueEntriesRunner.ChunkResult.Type.NON_APEX_PAGE);
    assertThat(chunk.tier()).isEqualTo(Tier.DIAMOND);
    verify(budgetTracker).recordSeedCall(1);
  }

  @Test
  @DisplayName("DIA 페이지가 빈 응답이면 advanceToNextDivision을 호출하고 quota를 카운트한다")
  void runNextChunk_whenDiaPageEmpty_advancesToNextDivisionAndCountsQuota() {
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
    LeagueEntriesFetchResult emptyResult = new LeagueEntriesFetchResult(1, 0, 0);
    given(leagueEntriesFetcher.execute(any())).willReturn(emptyResult);

    runner.runNextChunk();

    // 빈 응답 → division 전환 (I → II), quota 카운트
    assertThat(diaBucket.getCurrentDivision()).isEqualTo("II");
    assertThat(diaBucket.getCurrentPage()).isEqualTo(1);
    assertThat(diaBucket.getDailyPagesProcessed()).isEqualTo(1);
    verify(coverageBucketRepository).save(diaBucket);
  }

  @Test
  @DisplayName("DIA 페이지가 정상 응답이면 seedPage가 증가하고 quota를 카운트한다")
  void runNextChunk_whenDiaPageNotEmpty_advancesPageAndCountsQuota() {
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

    // 정상 응답
    LeagueEntriesFetchResult normalResult = new LeagueEntriesFetchResult(1, 10, 5);
    given(leagueEntriesFetcher.execute(any())).willReturn(normalResult);

    runner.runNextChunk();

    assertThat(diaBucket.getCurrentPage()).isEqualTo(2);
    assertThat(diaBucket.getDailyPagesProcessed()).isEqualTo(1);
    verify(coverageBucketRepository).save(diaBucket);
  }

  @Test
  @DisplayName("모든 티어 quota 소진 시 runNextChunk는 NO_WORK 반환")
  void runNextChunk_whenAllQuotaExhausted_returnsNoWork() {
    given(budgetTracker.canSeed()).willReturn(true);

    DailyPipelineState state = DailyPipelineState.create(LocalDate.now());
    state.recordSeedCompletedTier("CHALLENGER");
    state.recordSeedCompletedTier("GRANDMASTER");
    state.recordSeedCompletedTier("MASTER");
    given(budgetTracker.getOrCreateTodayState()).willReturn(state);

    given(patchVersionService.currentPatchVersion()).willReturn(Optional.of("15.23"));
    given(coverageBucketRepository.findByPatchAndTier("15.23", Tier.DIAMOND))
        .willReturn(Optional.of(bucketWithQuotaExhausted(Tier.DIAMOND, "15.23")));
    given(coverageBucketRepository.findByPatchAndTier("15.23", Tier.EMERALD))
        .willReturn(Optional.of(bucketWithQuotaExhausted(Tier.EMERALD, "15.23")));

    DailyLeagueEntriesRunner.ChunkResult result = runner.runNextChunk();

    assertThat(result.type()).isEqualTo(DailyLeagueEntriesRunner.ChunkResult.Type.NO_WORK);
  }

  // ── Phase 1: 버킷 자동 생성 ────────────────────────────────────────

  @Test
  @DisplayName("DIA 버킷이 DB에 없으면 hasWorkToday는 true를 반환한다")
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

  @Test
  @DisplayName("DIA 버킷이 DB에 없으면 runNextChunk는 버킷을 생성하고 DIA 페이지를 실행한다")
  void runNextChunk_whenDiaBucketMissing_createsBucketAndExecutesDiaPage() {
    given(budgetTracker.canSeed()).willReturn(true);

    DailyPipelineState state = DailyPipelineState.create(LocalDate.now());
    state.recordSeedCompletedTier("CHALLENGER");
    state.recordSeedCompletedTier("GRANDMASTER");
    state.recordSeedCompletedTier("MASTER");
    given(budgetTracker.getOrCreateTodayState()).willReturn(state);

    given(patchVersionService.currentPatchVersion()).willReturn(Optional.of("15.23"));
    given(coverageBucketRepository.findByPatchAndTier("15.23", Tier.DIAMOND)).willReturn(Optional.empty());

    CoverageBucket savedBucket = CoverageBucket.create("15.23", Tier.DIAMOND);
    given(coverageBucketRepository.save(argThat(b ->
        b.getTier() == Tier.DIAMOND && b.getPatch().equals("15.23")
    ))).willReturn(savedBucket);

    LeagueEntriesFetchResult seedResult = new LeagueEntriesFetchResult(1, 10, 3);
    given(leagueEntriesFetcher.execute(argThat(cmd ->
        "DIAMOND".equals(cmd.tier()) && cmd.page() == 1
    ))).willReturn(seedResult);

    DailyLeagueEntriesRunner.ChunkResult chunk = runner.runNextChunk();

    assertThat(chunk.type()).isEqualTo(DailyLeagueEntriesRunner.ChunkResult.Type.NON_APEX_PAGE);
    assertThat(chunk.tier()).isEqualTo(Tier.DIAMOND);
    // 버킷 생성 시 save 1회 이상 호출됨을 검증
    verify(coverageBucketRepository, atLeastOnce()).save(argThat(b ->
        b.getTier() == Tier.DIAMOND && b.getPatch().equals("15.23")
    ));
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

    DailyLeagueEntriesRunner.ChunkResult result = runner.runNextChunk();

    assertThat(result.type()).isEqualTo(DailyLeagueEntriesRunner.ChunkResult.Type.NO_WORK);
    verify(leagueEntriesFetcher, never()).execute(any());
    verify(coverageBucketRepository, never()).findByPatchAndTier(any(), any());
  }

  // ── Phase 2: quota 기반 ────────────────────────────────────────────

  @Test
  @DisplayName("DIA quota가 소진됐으면 hasWorkToday는 false를 반환한다")
  void hasWorkToday_whenDiaQuotaExhausted_returnsFalse() {
    given(budgetTracker.canSeed()).willReturn(true);

    DailyPipelineState state = DailyPipelineState.create(LocalDate.now());
    state.recordSeedCompletedTier("CHALLENGER");
    state.recordSeedCompletedTier("GRANDMASTER");
    state.recordSeedCompletedTier("MASTER");
    given(budgetTracker.getOrCreateTodayState()).willReturn(state);

    given(patchVersionService.currentPatchVersion()).willReturn(Optional.of("15.23"));
    given(coverageBucketRepository.findByPatchAndTier("15.23", Tier.DIAMOND))
        .willReturn(Optional.of(bucketWithQuotaExhausted(Tier.DIAMOND, "15.23")));
    given(coverageBucketRepository.findByPatchAndTier("15.23", Tier.EMERALD))
        .willReturn(Optional.of(bucketWithQuotaExhausted(Tier.EMERALD, "15.23")));

    assertThat(runner.hasWorkToday()).isFalse();
  }

  // ── helpers ────────────────────────────────────────────────────────

  private CoverageBucket bucketNotCompleted(Tier tier, String patch) {
    return CoverageBucket.create(patch, tier);
  }

  /** dailyPagesProcessed = quota(기본값 10)로 설정한 버킷 */
  private CoverageBucket bucketWithQuotaExhausted(Tier tier, String patch) {
    CoverageBucket bucket = CoverageBucket.create(patch, tier);
    for (int i = 0; i < props.getDiaEmeDailyPageQuota(); i++) {
      bucket.incrementDailyPagesProcessed();
    }
    return bucket;
  }
}
