package com.bestduo_BE.pipeline.application;

import com.bestduo_BE.common.application.PatchVersionService;
import com.bestduo_BE.common.domain.model.SeedBootstrapCommand;
import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.persistence.entity.DailyPipelineState;
import com.bestduo_BE.common.infra.riot.budget.DailyBudgetTracker;
import com.bestduo_BE.config.PipelineProperties;
import com.bestduo_BE.coverage.infra.persistence.entity.CoverageBucket;
import com.bestduo_BE.coverage.infra.persistence.repository.CoverageBucketJpaRepository;
import com.bestduo_BE.seed.application.SeedBootstrapExecutor;
import com.bestduo_BE.seed.application.SeedBootstrapExecutor.SeedBootstrapResult;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Stage 1 — 일일 Summoner 최신화.
 *
 * <ul>
 *   <li>CHALLENGER / GRANDMASTER / MASTER: 매일 전체 리스트 수집</li>
 *   <li>DIAMOND / EMERALD: {@link CoverageBucket}의 seedPage/seedDivision 기반 구간 순회</li>
 * </ul>
 *
 * 자정 경계는 {@link CoverageBucket#resetDailySeedIfNeeded} 와
 * {@link DailyPipelineState} 날짜 키(pipeline_date)로 자동 처리된다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DailySeedRunner {

  private static final String QUEUE = "RANKED_SOLO_5x5";
  private static final List<Tier> APEX_TIERS =
      List.of(Tier.CHALLENGER, Tier.GRANDMASTER, Tier.MASTER);
  private static final List<Tier> DIA_EME_TIERS =
      List.of(Tier.DIAMOND, Tier.EMERALD);

  private final SeedBootstrapExecutor seedBootstrapExecutor;
  private final CoverageBucketJpaRepository coverageBucketRepository;
  private final DailyBudgetTracker budgetTracker;
  private final PatchVersionService patchVersionService;
  private final PipelineProperties props;

  // ── public API ──────────────────────────────────────────────────────────

  /**
   * 오늘 처리할 seed 작업이 남아있으면 true.
   * 예산 소진 시 항상 false.
   */
  public boolean hasWorkToday() {
    if (!budgetTracker.canSeed()) {
      return false;
    }
    DailyPipelineState state = budgetTracker.getOrCreateTodayState();
    if (hasUncompletedApexTier(state)) {
      return true;
    }
    return hasUncompletedDiaEmeBucket();
  }

  /**
   * 다음 청크를 실행한다 (apex 1개 티어 전체 or DIA/EME 1 페이지).
   *
   * @return 청크 실행 결과
   */
  public ChunkResult runNextChunk() {
    if (!budgetTracker.canSeed()) {
      return ChunkResult.budgetExhausted();
    }

    DailyPipelineState state = budgetTracker.getOrCreateTodayState();

    // Phase A: apex 티어 (CHALLENGER → GRANDMASTER → MASTER)
    for (Tier tier : APEX_TIERS) {
      if (!state.getSeedCompletedTiers().contains("\"" + tier.name() + "\"")) {
        return runApexTierChunk(tier);
      }
    }

    // Phase B: DIA/EME 구간 순회
    String currentPatch = patchVersionService.currentPatchVersion().orElse(null);
    if (currentPatch == null) {
      log.warn("DIA/EME seed 스킵: 현재 패치 정보 없음");
      return ChunkResult.noWork();
    }
    for (Tier tier : DIA_EME_TIERS) {
      Optional<CoverageBucket> opt = coverageBucketRepository.findByPatchAndTier(currentPatch, tier);
      if (opt.isPresent() && !opt.get().isDailySeedCompleted()) {
        return runDiaEmePage(opt.get(), tier);
      }
    }

    return ChunkResult.noWork();
  }

  // ── private helpers ─────────────────────────────────────────────────────

  private boolean hasUncompletedApexTier(DailyPipelineState state) {
    for (Tier tier : APEX_TIERS) {
      if (!state.getSeedCompletedTiers().contains("\"" + tier.name() + "\"")) {
        return true;
      }
    }
    return false;
  }

  private boolean hasUncompletedDiaEmeBucket() {
    String currentPatch = patchVersionService.currentPatchVersion().orElse(null);
    if (currentPatch == null) {
      return false;
    }
    for (Tier tier : DIA_EME_TIERS) {
      Optional<CoverageBucket> opt = coverageBucketRepository.findByPatchAndTier(currentPatch, tier);
      if (opt.isPresent() && !opt.get().isDailySeedCompleted()) {
        return true;
      }
    }
    return false;
  }

  private ChunkResult runApexTierChunk(Tier tier) {
    log.info("Stage1 apex 티어 시작: tier={}", tier);
    // apex 티어는 항상 단일 페이지로 전체 목록이 반환되는 API 구조
    SeedBootstrapCommand cmd = new SeedBootstrapCommand(
        QUEUE, tier.name(), "I", tier, 1, 1, 0, 0);
    SeedBootstrapResult result = seedBootstrapExecutor.execute(cmd);

    // tier 완료 기록과 seed 호출 수를 단일 DB fetch로 원자적으로 저장
    int pages = result.pagesProcessed() > 0 ? result.pagesProcessed() : 1;
    budgetTracker.recordSeedCall(pages, tier.name());
    log.info("Stage1 apex 완료: tier={} seeded={}", tier, result.summonersSeeded());
    return ChunkResult.apexTier(tier, result.summonersSeeded());
  }

  private ChunkResult runDiaEmePage(CoverageBucket bucket, Tier tier) {
    log.info("Stage1 DIA/EME 페이지 시작: tier={} page={} division={}",
        tier, bucket.getSeedPage(), bucket.getSeedDivision());

    SeedBootstrapCommand cmd = new SeedBootstrapCommand(
        QUEUE,
        tier.name(),
        bucket.getSeedDivision(),
        tier,
        bucket.getSeedPage(),
        bucket.getSeedPage(),
        0,
        0
    );
    SeedBootstrapResult result = seedBootstrapExecutor.execute(cmd);

    budgetTracker.recordSeedCall(1);

    if (result.entriesFetched() == 0) {
      // 페이지가 비었으면 해당 bucket 오늘 완료로 표시
      bucket.markDailySeedCompleted();
      coverageBucketRepository.save(bucket);
      log.info("Stage1 DIA/EME bucket 완료: tier={}", tier);
    } else {
      // 다음 페이지/division으로 전진
      bucket.advanceSeedState(props.getMaxPagesPerDivision());
      coverageBucketRepository.save(bucket);
    }

    return ChunkResult.diaEmePage(tier, result.summonersSeeded());
  }

  // ── Result types ────────────────────────────────────────────────────────

  public record ChunkResult(Type type, Tier tier, int summonersSeeded) {

    public enum Type {
      APEX_TIER_CHUNK,
      DIA_EME_PAGE,
      BUDGET_EXHAUSTED,
      NO_WORK
    }

    public static ChunkResult apexTier(Tier tier, int seeded) {
      return new ChunkResult(Type.APEX_TIER_CHUNK, tier, seeded);
    }

    public static ChunkResult diaEmePage(Tier tier, int seeded) {
      return new ChunkResult(Type.DIA_EME_PAGE, tier, seeded);
    }

    public static ChunkResult budgetExhausted() {
      return new ChunkResult(Type.BUDGET_EXHAUSTED, null, 0);
    }

    public static ChunkResult noWork() {
      return new ChunkResult(Type.NO_WORK, null, 0);
    }
  }
}
