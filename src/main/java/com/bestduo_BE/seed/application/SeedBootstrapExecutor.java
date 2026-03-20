package com.bestduo_BE.seed.application;

import com.bestduo_BE.seed.application.port.LeagueEntriesSeedLoader;
import com.bestduo_BE.common.application.port.MatchIdsFinder;
import com.bestduo_BE.common.application.port.MatchQueueEnqueuer;
import com.bestduo_BE.seed.application.port.SummonerSeedRegistry;
import com.bestduo_BE.common.domain.model.SeedBootstrapCommand;
import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.riot.dto.LeagueEntry;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SeedBootstrapExecutor {

  private static final int PRIORITY_SEED = 50;

  private final LeagueEntriesSeedLoader leagueEntriesSeedLoader;
  private final MatchIdsFinder matchIdsFinder;
  private final MatchQueueEnqueuer matchQueueEnqueuer;
  private final SummonerSeedRegistry summonerSeedRegistry;

  public record SeedBootstrapResult(
      int pagesProcessed,
      int entriesFetched,
      int puuidRegistered,
      int matchIdsFetched,
      int matchIdsEnqueued
  ) {}

  /**
   * Phase2A(Seed Bootstrap):
   * - league entries에서 puuid 수집
   * - summoner 등록 + seed 상태 전이
   * - puuid의 matchIds를 "match_queue에 적재"까지만 수행
   * - match detail 처리(Phase1)는 MatchIngestWorker가 수행
   */
  public SeedBootstrapResult execute(SeedBootstrapCommand cmd) {
    int pagesProcessed = 0;
    int entriesFetched = 0;
    int puuidRegistered = 0;
    int matchIdsFetched = 0;
    int matchIdsEnqueued = 0;
    int processedEntries = 0;
    boolean limitEnabled = cmd.maxEntries() > 0;
    int maxEntries = cmd.maxEntries();

    for (int page = cmd.startPage(); page <= cmd.endPage(); page++) {
      List<LeagueEntry> entries = leagueEntriesSeedLoader.loadEntries(
          cmd.queue(), cmd.tier(), cmd.division(), page);

      if (entries == null || entries.isEmpty()) break;

      List<LeagueEntry> entriesToProcess = entries;
      if (limitEnabled) {
        int remainingSlots = maxEntries - processedEntries;
        if (remainingSlots <= 0) break;
        if (entries.size() > remainingSlots) {
          entriesToProcess = entries.subList(0, remainingSlots);
        }
      }

      if (entriesToProcess.isEmpty()) break;

      pagesProcessed++;
      entriesFetched += entriesToProcess.size();

      for (LeagueEntry e : entriesToProcess) {
        if (e == null) continue;

        String puuid = e.puuid();
        if (puuid == null || puuid.isBlank()) continue;

        processedEntries++;

        // 1) summoner 등록(멱등) - 처음 본 puuid만 진행
        boolean firstTime = summonerSeedRegistry.registerIfAbsent(puuid);
        if (!firstTime) continue;

        puuidRegistered++;

        // 2) seed status RUNNING
        summonerSeedRegistry.markSeedRunning(puuid);

        try {
          // 3) matchIds 가져오기 (외부 API)
          List<String> matchIds = matchIdsFinder.findRecentMatchIds(puuid, cmd.matchesPerPuuid());
          if (matchIds == null || matchIds.isEmpty()) {
            // matchIds가 없다면 seed는 "enqueue 할 게 없음"으로 DONE 처리
            summonerSeedRegistry.markSeedDone(puuid);
            continue;
          }

          matchIdsFetched += matchIds.size();

          // 4) ✅ matchIds를 match_queue에 적재 (detail 금지)
          Tier tierLabel = cmd.seedTier(); // "seed 입력 tier"를 저장 라벨로 사용
          matchQueueEnqueuer.enqueueAllIdempotent(matchIds, tierLabel, PRIORITY_SEED);
          matchIdsEnqueued += matchIds.size(); // (정확히는 "시도 수". 실제 신규 enqueue 수는 repo에서 카운트하려면 별도 처리 필요)

          // 5) seed status DONE (여기서 DONE은 "queue 적재 완료"의 의미)
          summonerSeedRegistry.markSeedDone(puuid);

        } catch (Exception ex) {
          summonerSeedRegistry.markSeedError(puuid);
          // Phase2A는 계속 진행 (다음 puuid로)
        }

        if (limitEnabled && processedEntries >= maxEntries) {
          break;
        }
      }

      if (limitEnabled && processedEntries >= maxEntries) {
        break;
      }
    }

    return new SeedBootstrapResult(
        pagesProcessed, entriesFetched, puuidRegistered, matchIdsFetched, matchIdsEnqueued
    );
  }
}
