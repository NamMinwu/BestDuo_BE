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
    SeedBootstrapProgress progress = new SeedBootstrapProgress(cmd.maxEntries());

    for (int page = cmd.startPage(); page <= cmd.endPage(); page++) {
      List<LeagueEntry> entries = loadEntriesForPage(cmd, page);

      if (entries == null || entries.isEmpty()) {
        break;
      }

      List<LeagueEntry> entriesToProcess = selectEntriesToProcess(entries, progress);

      if (entriesToProcess.isEmpty()) {
        break;
      }

      progress.recordPage(entriesToProcess.size());

      for (LeagueEntry entry : entriesToProcess) {
        processEntry(entry, cmd, progress);
        if (progress.reachedLimit()) {
          break;
        }
      }

      if (progress.reachedLimit()) {
        break;
      }
    }

    return progress.toResult();
  }

  private List<LeagueEntry> loadEntriesForPage(SeedBootstrapCommand cmd, int page) {
    return leagueEntriesSeedLoader.loadEntries(cmd.queue(), cmd.tier(), cmd.division(), page);
  }

  private List<LeagueEntry> selectEntriesToProcess(List<LeagueEntry> entries, SeedBootstrapProgress progress) {
    if (!progress.limitEnabled()) {
      return entries;
    }

    int remainingSlots = progress.remainingSlots();
    if (remainingSlots <= 0) {
      return List.of();
    }

    if (entries.size() <= remainingSlots) {
      return entries;
    }

    return entries.subList(0, remainingSlots);
  }

  private void processEntry(LeagueEntry entry, SeedBootstrapCommand cmd, SeedBootstrapProgress progress) {
    if (entry == null) {
      return;
    }

    String puuid = entry.puuid();
    if (puuid == null || puuid.isBlank()) {
      return;
    }

    progress.incrementProcessedEntries();

    if (!summonerSeedRegistry.registerIfAbsent(puuid)) {
      return;
    }

    progress.incrementRegisteredPuuids();
    processRegisteredPuuid(puuid, cmd, progress);
  }

  private void processRegisteredPuuid(String puuid, SeedBootstrapCommand cmd, SeedBootstrapProgress progress) {
    try {
      enqueueRecentMatches(puuid, cmd, progress);
    } catch (Exception ignored) {
    }
  }

  private void enqueueRecentMatches(String puuid, SeedBootstrapCommand cmd, SeedBootstrapProgress progress) {
    List<String> matchIds = matchIdsFinder.findRecentMatchIds(puuid, cmd.matchesPerPuuid());
    if (matchIds == null || matchIds.isEmpty()) {
      return;
    }

    progress.addFetchedMatchIds(matchIds.size());
    Tier tierLabel = cmd.seedTier();
    matchQueueEnqueuer.enqueueAllIdempotent(matchIds, tierLabel, PRIORITY_SEED);
    progress.addEnqueuedMatchIds(matchIds.size());
  }

  private static final class SeedBootstrapProgress {
    private final boolean limitEnabled;
    private final int maxEntries;
    private int pagesProcessed;
    private int entriesFetched;
    private int puuidRegistered;
    private int matchIdsFetched;
    private int matchIdsEnqueued;
    private int processedEntries;

    private SeedBootstrapProgress(int maxEntries) {
      this.limitEnabled = maxEntries > 0;
      this.maxEntries = maxEntries;
    }

    boolean limitEnabled() {
      return limitEnabled;
    }

    int remainingSlots() {
      return maxEntries - processedEntries;
    }

    boolean reachedLimit() {
      return limitEnabled && processedEntries >= maxEntries;
    }

    void recordPage(int entryCount) {
      pagesProcessed++;
      entriesFetched += entryCount;
    }

    void incrementProcessedEntries() {
      processedEntries++;
    }

    void incrementRegisteredPuuids() {
      puuidRegistered++;
    }

    void addFetchedMatchIds(int count) {
      matchIdsFetched += count;
    }

    void addEnqueuedMatchIds(int count) {
      matchIdsEnqueued += count;
    }

    SeedBootstrapResult toResult() {
      return new SeedBootstrapResult(
          pagesProcessed,
          entriesFetched,
          puuidRegistered,
          matchIdsFetched,
          matchIdsEnqueued
      );
    }
  }
}
