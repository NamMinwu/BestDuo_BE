package com.bestduo_BE.ingest.application;

import com.bestduo_BE.common.application.port.RiotApiPort;
import com.bestduo_BE.ingest.application.port.BottomDuoRawSaver;
import com.bestduo_BE.ingest.application.port.MatchSaver;
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

  private final RiotApiPort riotApiPort;
  private final MatchSaver matchSaver;
  private final BottomDuoRawSaver bottomDuoRawSaver;
  private final BottomDuoExtractor extractor;

  @Transactional
  public IngestResult execute(String matchId, Tier tier, String expectedPatch) {
    RiotMatchDto match = riotApiPort.loadMatch(matchId);
    List<BottomDuoRaw> raws = extractor.extract(matchId, match, tier);

    if (expectedPatch != null) {
      List<BottomDuoRaw> filtered = raws.stream()
          .filter(r -> expectedPatch.equals(r.patch()))
          .toList();
      if (filtered.size() < raws.size()) {
        log.warn("[PatchFilter] Discarded {} raws for matchId={} (expected={})",
            raws.size() - filtered.size(), matchId, expectedPatch);
      }
      if (filtered.isEmpty()) {
        log.debug("[PatchFilter] 패치 불일치로 match 저장 스킵: matchId={}", matchId);
        return new IngestResult(0, extractMatchStartTimeSec(match));
      }
      raws = filtered;
    }

    matchSaver.save(matchId, match);
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
