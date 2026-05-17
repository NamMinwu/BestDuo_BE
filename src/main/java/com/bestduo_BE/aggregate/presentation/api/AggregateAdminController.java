package com.bestduo_BE.aggregate.presentation.api;

import com.bestduo_BE.aggregate.application.ComputeBottomDuoRanking;
import com.bestduo_BE.common.domain.model.Tier;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Aggregate 영역의 일회성 수동 트리거.
 *
 * <p>주 용도: 스케줄러가 aggregate 만 수행하고 ranking 호출이 빠졌던 회귀(regression) 이후,
 * 해당 patch 의 PENDING 행을 RANKED 로 승격시키기 위한 복구 endpoint.
 *
 * <p>인증: {@code AdminApiKeyInterceptor} 가 {@code X-Admin-Key} 헤더를 검증한다.
 */
@RestController
@RequestMapping("/admin/aggregate")
@RequiredArgsConstructor
@Slf4j
public class AggregateAdminController {

  private static final List<Tier> DEFAULT_TIERS = List.of(
      Tier.CHALLENGER, Tier.GRANDMASTER, Tier.MASTER, Tier.DIAMOND, Tier.EMERALD);

  private final ComputeBottomDuoRanking rankingUseCase;

  @PostMapping("/recompute-ranking")
  public RecomputeRankingResponse recomputeRanking(
      @RequestParam String patch,
      @RequestParam(required = false) List<Tier> tiers) {

    List<Tier> targetTiers = (tiers == null || tiers.isEmpty()) ? DEFAULT_TIERS : tiers;
    List<TierResult> results = new ArrayList<>();
    int totalUpdated = 0;

    for (Tier tier : targetTiers) {
      try {
        ComputeBottomDuoRanking.Result r = rankingUseCase.execute(patch, tier.name());
        results.add(new TierResult(tier.name(), r.updatedRows(), null));
        totalUpdated += r.updatedRows();
      } catch (Exception ex) {
        log.error("[AggregateAdmin] ranking failed patch={} tier={}", patch, tier, ex);
        results.add(new TierResult(tier.name(), 0, ex.getMessage()));
      }
    }

    log.info("[AggregateAdmin] recompute-ranking complete patch={} tiers={} totalUpdated={}",
        patch, targetTiers, totalUpdated);
    return new RecomputeRankingResponse(patch, totalUpdated, results);
  }

  public record RecomputeRankingResponse(String patch, int totalUpdated, List<TierResult> results) {}

  public record TierResult(String tier, int updatedRows, String error) {}
}
