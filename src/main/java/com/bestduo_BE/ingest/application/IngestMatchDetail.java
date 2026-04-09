package com.bestduo_BE.ingest.application;

import com.bestduo_BE.ingest.application.port.BottomDuoRawSaver;
import com.bestduo_BE.ingest.application.port.MatchSaver;
import com.bestduo_BE.ingest.application.port.RiotMatchLoader;
import com.bestduo_BE.common.application.port.SummonerExpansionQueue;
import com.bestduo_BE.common.domain.model.BottomDuoRaw;
import com.bestduo_BE.common.domain.model.IngestResult;
import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.domain.service.BottomDuoExtractor;
import com.bestduo_BE.common.infra.riot.dto.RiotMatchDto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class IngestMatchDetail {

  private final RiotMatchLoader riotMatchLoader;
  private final MatchSaver matchSaver;
  private final BottomDuoRawSaver bottomDuoRawSaver;

  private final SummonerExpansionQueue summonerExpandQueue;

  private final BottomDuoExtractor extractor = new BottomDuoExtractor();

  @Transactional
  public IngestResult execute(String matchId, Tier tier, String expectedPatch) {
    RiotMatchDto match = loadMatch(matchId);
    saveMatch(matchId, match);
    List<BottomDuoRaw> raws = extractBottomDuoRaws(matchId, match, tier);

    if (expectedPatch != null) {
      List<BottomDuoRaw> filtered = raws.stream()
          .filter(r -> expectedPatch.equals(r.patch()))
          .toList();
      if (filtered.size() < raws.size()) {
        log.warn("[PatchFilter] Discarded {} raws for matchId={} (expected={})",
            raws.size() - filtered.size(), matchId, expectedPatch);
      }
      raws = filtered;
    }

    saveBottomDuoRaws(raws);
    expandParticipants(match);
    Long startSec = extractMatchStartTimeSec(match);
    return new IngestResult(raws.size(), startSec);
  }

  private RiotMatchDto loadMatch(String matchId) {
    return riotMatchLoader.loadMatch(matchId);
  }

  private void saveMatch(String matchId, RiotMatchDto match) {
    matchSaver.save(matchId, match);
  }

  private List<BottomDuoRaw> extractBottomDuoRaws(String matchId, RiotMatchDto match, Tier tier) {
    return extractor.extract(matchId, match, tier);
  }

  private void saveBottomDuoRaws(List<BottomDuoRaw> raws) {
    bottomDuoRawSaver.saveAllIdempotent(raws);
  }

  private void expandParticipants(RiotMatchDto match) {
    expandSeedsFromParticipants(match);
  }

  private int expandSeedsFromParticipants(RiotMatchDto match) {
    if (match == null || match.metadata() == null) {
      return 0;
    }

    List<String> participants = match.metadata().participants();
    if (participants == null || participants.isEmpty()) {
      return 0;
    }

    int created = 0;
    for (String p : participants) {
      if (p == null) {
        continue;
      }

      String puuid = p.trim();
      if (puuid.isEmpty()) {
        continue;
      }

      if (summonerExpandQueue.registerIfAbsent(puuid)) {
        created++;
      }
    }
    return created;
  }

  private Long extractMatchStartTimeSec(RiotMatchDto match) {
    if (match == null || match.info() == null) {
      return null;
    }

    Long ms = match.info().gameStartTimestamp();
    if (ms == null) {
      return null;
    }

    return ms / 1000L;
  }
}
