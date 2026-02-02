package com.bestduo_BE.application;

import com.bestduo_BE.application.port.LeagueEntriesRefreshLoader;
import com.bestduo_BE.application.port.MatchIdsFinder;
import com.bestduo_BE.domain.model.CollectResult;
import com.bestduo_BE.domain.model.Tier;
import com.bestduo_BE.infra.persistence.entity.Summoner;
import com.bestduo_BE.infra.persistence.repository.SummonerJpaRepository;
import com.bestduo_BE.infra.riot.dto.LeagueEntry;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshSummonerMatches {

  private static final int FETCH_COUNT = 50;

  private final SummonerJpaRepository summonerJpaRepository;
  private final LeagueEntriesRefreshLoader leagueEntriesRefreshLoader;
  private final MatchIdsFinder matchIdsFinder;

  private final CollectMatchDetailAndSaveRaw collectMatchDetailAndSaveRaw;

  @Transactional
  public Result execute(String puuid) {
    Summoner s = summonerJpaRepository.findById(puuid)
        .orElseGet(() -> summonerJpaRepository.save(Summoner.newReady(puuid)));

    try {
      s.markRefreshRunning();

      Tier collectionTier = resolveCollectionTierBySolo(puuid);
      if (collectionTier == null || collectionTier == Tier.ALL_TIERS) {
        s.markRefreshDone(s.getLastMatchStartTime());
        return new Result(puuid, 0, collectionTier, s.getLastMatchStartTime());
      }

      List<String> matchIds = loadMatchIds(puuid, s.getLastMatchStartTime());

      int totalRawCreated = 0;
      Long newestStartTime = s.getLastMatchStartTime();

      for (String matchId : matchIds) {
        CollectResult r = collectMatchDetailAndSaveRaw.execute(matchId, collectionTier);
        totalRawCreated += r.rawCreated();

        if (r.matchStartTimeSec() != null) {
          newestStartTime = (newestStartTime == null)
              ? r.matchStartTimeSec()
              : Math.max(newestStartTime, r.matchStartTimeSec());
        }
      }

      s.markRefreshDone(newestStartTime);
      return new Result(puuid, totalRawCreated, collectionTier, newestStartTime);

    } catch (Exception e) {
      log.error("Refresh failed. puuid={}", puuid, e);
      s.markRefreshError();
      throw e;
    }
  }

  private List<String> loadMatchIds(String puuid, Long lastMatchStartTimeSecOrNull) {
    if (lastMatchStartTimeSecOrNull == null) {
      return matchIdsFinder.findRecentMatchIds(puuid, FETCH_COUNT);
    }
    return matchIdsFinder.findMatchIdsSince(puuid, lastMatchStartTimeSecOrNull, FETCH_COUNT);
  }

  /**
   * Riot entries/by-puuid에서 SOLO 티어를 가져와서
   * 서비스 저장 기준(Tier)로 변환한다.
   */
  private Tier resolveCollectionTierBySolo(String puuid) {
    List<LeagueEntry> entries = leagueEntriesRefreshLoader.loadEntriesByPuuid(puuid);

    LeagueEntry solo = entries.stream()
        .filter(e -> "RANKED_SOLO_5x5".equals(e.queueType()))
        .max(Comparator.comparingLong(LeagueEntry::leaguePoints))
        .orElse(null);

    if (solo == null) return null;

    Tier riotTier;
    try {
      riotTier = Tier.valueOf(solo.tier()); // e.g. "EMERALD"
    } catch (IllegalArgumentException e) {
      return null;
    }

    // ✅ bucket 정책 (필요하면 여기만 바꾸면 됨)
    return switch (riotTier) {
      case EMERALD -> Tier.EMERALD_PLUS;
      // DIAMOND_PLUS가 enum에 없으니, 상위는 DIAMOND로 묶는 예시
      default -> riotTier;
    };
  }

  public record Result(
      String puuid,
      int rawCreated,
      Tier collectionTier,
      Long lastMatchStartTimeSec
  ) {}
}
