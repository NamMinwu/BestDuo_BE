package com.bestduo_BE.leagueentry.application;

import com.bestduo_BE.common.application.port.RiotApiPort;
import com.bestduo_BE.common.domain.model.LeagueEntriesFetchCommand;
import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.riot.dto.LeagueEntry;
import com.bestduo_BE.leagueentry.application.port.SummonerSeedRegistry;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Phase 3 이후: league entries → summoner upsert + seededAt 갱신.
 * matchIds enqueue는 Stage 2(CollectMatchIdsRunner)가 담당한다.
 */
@Service
@RequiredArgsConstructor
public class LeagueEntriesFetcher {

  private final RiotApiPort riotApiPort;
  private final SummonerSeedRegistry summonerSeedRegistry;

  public record LeagueEntriesFetchResult(
      int pagesProcessed,
      int entriesFetched,
      int summonersSeeded
  ) {}

  /**
   * 주어진 커맨드에 따라 league entries를 페이지 단위로 읽어
   * 각 summoner를 upsert하고 seededAt을 기록한다.
   */
  public LeagueEntriesFetchResult execute(LeagueEntriesFetchCommand cmd) {
    FetchProgress progress = new FetchProgress(cmd.maxEntries());

    List<LeagueEntry> entries = riotApiPort.loadLeagueEntries(
        cmd.queue(), cmd.tier(), cmd.division(), cmd.page());

    if (entries != null && !entries.isEmpty()) {
      List<LeagueEntry> toProcess = selectEntriesToProcess(entries, progress);
      if (!toProcess.isEmpty()) {
        progress.recordPage(toProcess.size());
        for (LeagueEntry entry : toProcess) {
          processEntry(entry, cmd, progress);
          if (progress.reachedLimit()) {
            break;
          }
        }
      }
    }

    return progress.toResult();
  }

  private List<LeagueEntry> selectEntriesToProcess(List<LeagueEntry> entries,
      FetchProgress progress) {
    if (!progress.limitEnabled()) {
      return entries;
    }
    int remaining = progress.remainingSlots();
    if (remaining <= 0) {
      return List.of();
    }
    return entries.size() <= remaining ? entries : entries.subList(0, remaining);
  }

  private void processEntry(LeagueEntry entry, LeagueEntriesFetchCommand cmd,
      FetchProgress progress) {
    if (entry == null) {
      return;
    }
    String puuid = entry.puuid();
    if (puuid == null || puuid.isBlank()) {
      return;
    }

    Tier tier = parseTier(entry.tier(), cmd.seedTier());
    summonerSeedRegistry.upsertLeagueEntry(puuid, tier, OffsetDateTime.now());
    progress.incrementSeeded();
  }

  private static Tier parseTier(String tierStr, Tier fallback) {
    if (tierStr != null && !tierStr.isBlank()) {
      try {
        return Tier.valueOf(tierStr.toUpperCase());
      } catch (IllegalArgumentException ignored) {
      }
    }
    return fallback;
  }

  private static final class FetchProgress {

    private final boolean limitEnabled;
    private final int maxEntries;
    private int pagesProcessed;
    private int entriesFetched;
    private int summonersSeeded;
    private int processedEntries;

    private FetchProgress(int maxEntries) {
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

    void incrementSeeded() {
      processedEntries++;
      summonersSeeded++;
    }

    LeagueEntriesFetchResult toResult() {
      return new LeagueEntriesFetchResult(pagesProcessed, entriesFetched, summonersSeeded);
    }
  }
}
