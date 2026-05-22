package com.bestduo_BE.pipeline.application;

import com.bestduo_BE.common.application.PatchVersionService;
import com.bestduo_BE.common.domain.model.EffectivePatchContext;
import com.bestduo_BE.common.domain.model.LeagueEntriesFetchCommand;
import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.persistence.entity.DailyPipelineState;
import com.bestduo_BE.common.infra.persistence.repository.DailyPipelineStateJpaRepository;
import com.bestduo_BE.config.PipelineProperties;
import com.bestduo_BE.coverage.infra.persistence.entity.CoverageBucket;
import com.bestduo_BE.coverage.infra.persistence.repository.CoverageBucketJpaRepository;
import com.bestduo_BE.leagueentry.application.LeagueEntriesFetcher;
import com.bestduo_BE.leagueentry.application.LeagueEntriesFetcher.LeagueEntriesFetchResult;
import java.time.LocalDate;
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
 *   <li>DIAMOND / EMERALD: {@link CoverageBucket}의 currentPage/currentDivision 기반 구간 순회</li>
 * </ul>
 *
 * 자정 경계는 {@link CoverageBucket#resetDailyPagesIfNeeded} 와
 * {@link DailyPipelineState} 날짜 키(pipeline_date)로 자동 처리된다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DailyLeagueEntriesRunner {

  private static final String QUEUE = "RANKED_SOLO_5x5";
  private static final List<Tier> NON_APEX_TIERS =
      List.of(Tier.DIAMOND, Tier.EMERALD);

  private final LeagueEntriesFetcher leagueEntriesFetcher;
  private final CoverageBucketJpaRepository coverageBucketRepository;
  private final DailyPipelineStateJpaRepository stateRepository;
  private final PatchVersionService patchVersionService;
  private final PipelineProperties props;

  // ── public API ──────────────────────────────────────────────────────────

  /**
   * 오늘 처리할 seed 작업이 남아있으면 true.
   */
  public boolean hasWorkToday() {
    DailyPipelineState state = stateRepository.getOrCreateForDate(LocalDate.now());
    if (hasUncompletedApexTier(state)) {
      return true;
    }
    return hasUncompletedNonApexBucket();
  }

  /**
   * 다음 청크를 실행한다 (apex 1개 티어 전체 or DIA/EME 1 페이지).
   *
   * @return 청크 실행 결과
   */
  public ChunkResult runNextChunk() {
    DailyPipelineState state = stateRepository.getOrCreateForDate(LocalDate.now());

    // Phase A: apex 티어 (CHALLENGER → GRANDMASTER → MASTER)
    for (Tier tier : Tier.APEX_TIERS) {
      if (!state.isSeedTierCompleted(tier)) {
        return runApexTierChunk(tier);
      }
    }

    // Phase B: non-apex 구간 순회 (DIAMOND → EMERALD)
    String effectivePatch = patchVersionService.resolveEffectivePatchContext()
        .map(EffectivePatchContext::patch)
        .orElse(null);

    if (effectivePatch == null) {
      log.warn("non-apex seed 스킵: 현재 패치 정보 없음");
      return ChunkResult.noWork();
    }
    LocalDate today = LocalDate.now();
    for (Tier tier : NON_APEX_TIERS) {
      CoverageBucket bucket = getOrCreateBucket(effectivePatch, tier);
      bucket.resetDailyPagesIfNeeded(bucket.getDailyPagesResetAt(), today);
      if (bucket.hasRemainingDailyQuota(props.getDiaEmeDailyPageQuota())) {
        return runNonApexPage(bucket, tier);
      }
    }

    return ChunkResult.noWork();
  }

  // ── private helpers ─────────────────────────────────────────────────────

  private boolean hasUncompletedApexTier(DailyPipelineState state) {
    for (Tier tier : Tier.APEX_TIERS) {
      if (!state.isSeedTierCompleted(tier)) {
        return true;
      }
    }
    return false;
  }

  private boolean hasUncompletedNonApexBucket() {
    String effectivePatch = patchVersionService.resolveEffectivePatchContext()
        .map(EffectivePatchContext::patch)
        .orElse(null);
    if (effectivePatch == null) {
      return false;
    }
    LocalDate today = LocalDate.now();
    for (Tier tier : NON_APEX_TIERS) {
      Optional<CoverageBucket> opt = coverageBucketRepository.findByPatchAndTier(effectivePatch, tier);
      if (opt.isEmpty()) {
        return true;
      }
      CoverageBucket bucket = opt.get();
      // 자정 경계 확인은 in-memory 에서만 수행 (영속화는 runNextChunk → runNonApexPage 의 save 가 담당)
      bucket.resetDailyPagesIfNeeded(bucket.getDailyPagesResetAt(), today);
      if (bucket.hasRemainingDailyQuota(props.getDiaEmeDailyPageQuota())) {
        return true;
      }
    }
    return false;
  }

  private CoverageBucket getOrCreateBucket(String patch, Tier tier) {
    return coverageBucketRepository.findByPatchAndTier(patch, tier)
        .orElseGet(() -> {
          CoverageBucket newBucket = CoverageBucket.create(patch, tier);
          log.info("CoverageBucket 자동 생성: patch={} tier={}", patch, tier);
          return coverageBucketRepository.save(newBucket);
        });
  }

  private ChunkResult runApexTierChunk(Tier tier) {
    log.info("Stage1 apex 티어 시작: tier={}", tier);
    // apex 티어는 항상 단일 페이지로 전체 목록이 반환되는 API 구조
    LeagueEntriesFetchCommand cmd = new LeagueEntriesFetchCommand(
        QUEUE, tier.name(), "I", tier, 1, 0);
    LeagueEntriesFetchResult result = leagueEntriesFetcher.execute(cmd);

    // tier 완료 기록 (호출량 카운트는 PipelineMetrics 가 stage 카운터로 추적)
    DailyPipelineState state = stateRepository.getOrCreateForDate(LocalDate.now());
    state.recordSeedCompletedTier(tier);
    stateRepository.save(state);
    log.info("Stage1 apex 완료: tier={} seeded={}", tier, result.summonersSeeded());
    return ChunkResult.apexTier(tier, result.summonersSeeded());
  }

  private ChunkResult runNonApexPage(CoverageBucket bucket, Tier tier) {
    log.info("Stage1 non-apex 페이지 시작: tier={} page={} division={}",
        tier, bucket.getCurrentPage(), bucket.getCurrentDivision());

    LeagueEntriesFetchCommand cmd = new LeagueEntriesFetchCommand(
        QUEUE,
        tier.name(),
        bucket.getCurrentDivision(),
        tier,
        bucket.getCurrentPage(),
        0
    );
    LeagueEntriesFetchResult result = leagueEntriesFetcher.execute(cmd);

    if (result.entriesFetched() == 0) {
      // 빈 응답 = 해당 division 소진 → 다음 division으로 이동
      bucket.advanceToNextDivision();
      log.info("Stage1 non-apex division 소진, 다음 division으로 이동: tier={} division={}",
          tier, bucket.getCurrentDivision());
    } else {
      // 정상 응답: 다음 페이지로 전진 (safety cap 도달 시 advanceToNextDivision 위임)
      bucket.advancePageState(props.getMaxPagesPerDivision());
    }
    bucket.incrementDailyPagesProcessed();
    coverageBucketRepository.save(bucket);

    return ChunkResult.nonApexPage(tier, result.summonersSeeded());
  }

  // ── Result types ────────────────────────────────────────────────────────

  public record ChunkResult(Type type, Tier tier, int summonersSeeded) {

    public enum Type {
      APEX_TIER_CHUNK,
      NON_APEX_PAGE,
      NO_WORK
    }

    public static ChunkResult apexTier(Tier tier, int seeded) {
      return new ChunkResult(Type.APEX_TIER_CHUNK, tier, seeded);
    }

    public static ChunkResult nonApexPage(Tier tier, int seeded) {
      return new ChunkResult(Type.NON_APEX_PAGE, tier, seeded);
    }

    public static ChunkResult noWork() {
      return new ChunkResult(Type.NO_WORK, null, 0);
    }
  }
}
