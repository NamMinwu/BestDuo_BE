package com.bestduo_BE.application;

import com.bestduo_BE.application.port.LeagueEntriesSeedLoader;
import com.bestduo_BE.application.port.MatchIdsFinder;
import com.bestduo_BE.application.port.SummonerSeedRegistry;
import com.bestduo_BE.domain.model.SeedBootstrapCommand;
import com.bestduo_BE.infra.riot.dto.LeagueEntry;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SeedBootstrapRun {

  private final LeagueEntriesSeedLoader leagueEntriesSeedLoader;
  private final MatchIdsFinder matchIdsFinder;
  private final SummonerSeedRegistry summonerSeedRegistry;

  private final CollectMatchDetailAndSaveRaw collectMatchDetailAndSaveRaw;

  public record SeedBootstrapResult(
      int pagesProcessed,
      int entriesFetched,
      int puuidRegistered,
      int matchIdsFetched,
      int rawCreated
  ) {}

  // 리펙이 필요할 듯
  public SeedBootstrapResult execute(SeedBootstrapCommand cmd) {
    int pagesProcessed = 0;
    int entriesFetched = 0;
    int puuidRegistered = 0;
    int matchIdsFetched = 0;
    int rawCreated = 0;

    for (int page = cmd.startPage(); page <= cmd.endPage(); page++) {

      List<LeagueEntry> entries = leagueEntriesSeedLoader.loadEntries(cmd.queue(), cmd.tier(), cmd.division(), page);
      if (entries == null || entries.isEmpty()) break;

      pagesProcessed++;
      entriesFetched += entries.size();

      for (LeagueEntry e : entries) {
        if (e == null) continue;

        String puuid = e.puuid();
        if (puuid == null || puuid.isBlank()) {
          // PUUID-only 전제라면 여기서는 skip
          continue;
        }

        boolean firstTime = summonerSeedRegistry.registerIfAbsent(puuid);
        if (!firstTime) continue;

        puuidRegistered++;

        summonerSeedRegistry.markSeedRunning(puuid);
        try {
          List<String> matchIds = matchIdsFinder.findRecentMatchIds(puuid, cmd.matchesPerPuuid());
          if (matchIds == null || matchIds.isEmpty()) {
            summonerSeedRegistry.markSeedDone(puuid);
            continue;
          }

          matchIdsFetched += matchIds.size();

          for (String matchId : matchIds) {
            if (matchId == null || matchId.isBlank()) continue;
            rawCreated += collectMatchDetailAndSaveRaw.execute(matchId, cmd.seedTier()).rawCreated();
          }

          summonerSeedRegistry.markSeedDone(puuid);

        } catch (Exception ex) {
          summonerSeedRegistry.markSeedError(puuid);
          // Phase2A는 계속 진행(운영상 보통 유리)
        }
      }
    }

    return new SeedBootstrapResult(pagesProcessed, entriesFetched, puuidRegistered, matchIdsFetched, rawCreated);
  }


}
