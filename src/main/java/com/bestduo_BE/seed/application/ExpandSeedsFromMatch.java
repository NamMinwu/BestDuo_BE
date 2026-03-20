package com.bestduo_BE.seed.application;

import com.bestduo_BE.common.application.port.MatchPayloadReader;
import com.bestduo_BE.common.application.port.SummonerExpandQueue;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ExpandSeedsFromMatch {

  private final MatchPayloadReader matchPayloadReader;
  private final SummonerExpandQueue summonerExpandQueue;

  /**
   * @return 새로 등록된 seed puuid 개수
   */
  public int execute(String matchId) {
    var match = matchPayloadReader.read(matchId);

    List<String> participants = match.metadata().participants();
    if (participants == null || participants.isEmpty()) return 0;

    int created = 0;
    for (String puuid : participants) {
      if (puuid == null || puuid.isBlank()) continue;
      if (summonerExpandQueue.registerIfAbsent(puuid)) created++;
    }
    return created;
  }
}
