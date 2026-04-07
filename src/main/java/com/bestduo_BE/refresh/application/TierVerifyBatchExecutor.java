package com.bestduo_BE.refresh.application;

import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.persistence.repository.SummonerJpaRepository;
import com.bestduo_BE.common.infra.riot.dto.LeagueEntry;
import com.bestduo_BE.refresh.application.port.LeagueEntriesRefreshLoader;
import com.bestduo_BE.refresh.application.port.SummonerRefreshStatusUpdater;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 소환사의 실제 티어를 Riot league-v4 API로 검증하고 lastKnownTier / tierObservedAt을 갱신한다.
 * tierObservedAt IS NULL인 소환사(미검증)를 우선 처리한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TierVerifyBatchExecutor {

  private final SummonerJpaRepository summonerJpaRepository;
  private final LeagueEntriesRefreshLoader leagueEntriesRefreshLoader;
  private final SummonerRefreshStatusUpdater summonerRefreshStatusUpdater;

  public Result execute(int limit, Tier requestedTier) {
    // findRefreshTargets: tierObservedAt IS NULL 우선 정렬 → 미검증 소환사 먼저 처리
    var targets = summonerJpaRepository.findRefreshTargets(limit, requestedTier.name());

    int processed = 0, success = 0, failed = 0;
    for (var summoner : targets) {
      processed++;
      try {
        List<LeagueEntry> entries = leagueEntriesRefreshLoader.loadEntriesByPuuid(summoner.getPuuid());
        LeagueEntry soloEntry = entries.stream()
            .filter(e -> "RANKED_SOLO_5x5".equals(e.queueType()))
            .findFirst()
            .orElse(null);

        if (soloEntry != null && soloEntry.tier() != null) {
          Tier verifiedTier = Tier.valueOf(soloEntry.tier());
          summonerRefreshStatusUpdater.syncResolvedTier(
              summoner.getPuuid(), verifiedTier, OffsetDateTime.now());
          success++;
        } else {
          // 랭크 게임 기록 없음 — tier 정보 없이 관측 시각만 기록해 재검증 방지
          summonerRefreshStatusUpdater.syncResolvedTier(
              summoner.getPuuid(), null, OffsetDateTime.now());
          failed++;
        }
      } catch (Exception e) {
        failed++;
        log.warn("TierVerifyBatchExecutor failed. puuid={}", summoner.getPuuid(), e);
        // tierObservedAt을 갱신해 이 소환사가 다음 주기에 즉시 재시도되지 않도록 한다.
        // (갱신하지 않으면 findRefreshTargets가 tierObservedAt IS NULL 우선으로 계속 뽑아 무한 재시도 발생)
        try {
          summonerRefreshStatusUpdater.syncResolvedTier(summoner.getPuuid(), null, OffsetDateTime.now());
        } catch (Exception ignored) {
          // DB 갱신 실패는 무시 — 주 목적은 메인 루프 유지
        }
      }
    }
    return new Result(processed, success, failed);
  }

  public record Result(int processed, int success, int failed) {
  }
}
