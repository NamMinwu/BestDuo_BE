package com.bestduo_BE.ingest.application;

import com.bestduo_BE.ingest.application.port.BottomDuoRawSaver;
import com.bestduo_BE.ingest.application.port.MatchSaver;
import com.bestduo_BE.ingest.application.port.RiotMatchLoader;
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

  private final BottomDuoExtractor extractor = new BottomDuoExtractor();

  @Transactional
  public IngestResult execute(String matchId, Tier tier, String expectedPatch) {
    RiotMatchDto match = riotMatchLoader.loadMatch(matchId);
    matchSaver.save(matchId, match);
    List<BottomDuoRaw> raws = extractor.extract(matchId, match, tier);

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

    bottomDuoRawSaver.saveAllIdempotent(raws);
    return new IngestResult(raws.size(), extractMatchStartTimeSec(match));
  }

  private Long extractMatchStartTimeSec(RiotMatchDto match) {
    if (match == null || match.info() == null) {
      return null;
    }
    Long ms = match.info().gameStartTimestamp();
    return ms == null ? null : ms / 1000L;
  }
}
