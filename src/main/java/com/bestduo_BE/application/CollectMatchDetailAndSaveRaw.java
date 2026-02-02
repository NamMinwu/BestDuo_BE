package com.bestduo_BE.application;

import com.bestduo_BE.application.port.BottomDuoRawSaver;
import com.bestduo_BE.application.port.MatchSaver;
import com.bestduo_BE.application.port.RiotMatchLoader;
import com.bestduo_BE.domain.model.BottomDuoRaw;
import com.bestduo_BE.domain.model.CollectResult;
import com.bestduo_BE.domain.model.Tier;
import com.bestduo_BE.domain.service.BottomDuoExtractor;
import com.bestduo_BE.infra.riot.dto.RiotMatchDto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CollectMatchDetailAndSaveRaw {
  private final RiotMatchLoader riotMatchLoader;
  private final MatchSaver matchSaver;
  private final BottomDuoRawSaver bottomDuoRawSaver;

  private final BottomDuoExtractor extractor = new BottomDuoExtractor();

  @Transactional
  public CollectResult execute(String matchId, Tier tier) {
    RiotMatchDto match = riotMatchLoader.loadMatch(matchId);
    matchSaver.save(matchId, match);

    List<BottomDuoRaw> raws = extractor.extract(matchId, match, tier);
    bottomDuoRawSaver.saveAllIdempotent(raws);

    Long startSec = extractMatchStartTimeSec(match);
    return new CollectResult(raws.size(), startSec);
  }

  private Long extractMatchStartTimeSec(RiotMatchDto match) {
    if (match == null || match.info() == null) return null;
    Long ms = match.info().gameStartTimestamp(); // RiotMatchDto에 있어야 함
    if (ms == null) return null;
    return ms / 1000L;
  }
}
