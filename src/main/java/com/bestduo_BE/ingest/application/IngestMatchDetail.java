package com.bestduo_BE.ingest.application;

import com.bestduo_BE.ingest.application.port.BottomDuoRawSaver;
import com.bestduo_BE.ingest.application.port.MatchSaver;
import com.bestduo_BE.ingest.application.port.RiotMatchLoader;
import com.bestduo_BE.common.application.port.SummonerExpandQueue;
import com.bestduo_BE.common.domain.model.BottomDuoRaw;
import com.bestduo_BE.common.domain.model.IngestResult;
import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.domain.service.BottomDuoExtractor;
import com.bestduo_BE.common.infra.riot.dto.RiotMatchDto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IngestMatchDetail {

  private final RiotMatchLoader riotMatchLoader;
  private final MatchSaver matchSaver;
  private final BottomDuoRawSaver bottomDuoRawSaver;

  // ✅ 추가: participant puuid를 summoner로 확장(멱등)
  private final SummonerExpandQueue summonerExpandQueue;

  private final BottomDuoExtractor extractor = new BottomDuoExtractor();

  @Transactional
  public IngestResult execute(String matchId, Tier tier) {
    RiotMatchDto match = riotMatchLoader.loadMatch(matchId);

    // 1) match 저장 (payload_json)
    matchSaver.save(matchId, match);

    // 2) raw 저장 (바텀 듀오)
    List<BottomDuoRaw> raws = extractor.extract(matchId, match, tier);
    bottomDuoRawSaver.saveAllIdempotent(raws);

    // ✅ 3) participants puuid → summoner upsert(멱등)
    // - metadata.participants는 match에 참여한 10 puuid
    int expanded = expandSeedsFromParticipants(match);

    Long startSec = extractMatchStartTimeSec(match);

    // IngestResult에 expanded까지 넣고 싶으면 record를 확장하면 됨.
    // 지금은 로깅/메트릭으로만 써도 충분.
    return new IngestResult(raws.size(), startSec);
  }

  private int expandSeedsFromParticipants(RiotMatchDto match) {
    if (match == null || match.metadata() == null) return 0;

    List<String> participants = match.metadata().participants();
    if (participants == null || participants.isEmpty()) return 0;

    int created = 0;
    for (String p : participants) {
      if (p == null) continue;
      String puuid = p.trim();
      if (puuid.isEmpty()) continue;

      // registerIfAbsent 내부가 ON CONFLICT DO NOTHING이면
      // 중복/순서는 신경 안 써도 됨.
      if (summonerExpandQueue.registerIfAbsent(puuid)) created++;
    }
    return created;
  }

  private Long extractMatchStartTimeSec(RiotMatchDto match) {
    if (match == null || match.info() == null) return null;
    Long ms = match.info().gameStartTimestamp(); // RiotMatchDto에 있어야 함
    if (ms == null) return null;
    return ms / 1000L;
  }
}
