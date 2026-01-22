package com.bestduo_BE.infra.persistence;

import com.bestduo_BE.application.port.MatchSaver;
import com.bestduo_BE.infra.persistence.entity.Match;
import com.bestduo_BE.infra.persistence.repository.MatchJpaRepository;
import com.bestduo_BE.infra.riot.dto.InfoDto;
import com.bestduo_BE.infra.riot.dto.RiotMatchDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class MatchSaverImpl implements MatchSaver {

  private final MatchJpaRepository matchRepository;
  private final ObjectMapper objectMapper;

  @Override
  public void save(String matchId, RiotMatchDto matchDetail) {
    InfoDto info = matchDetail.info();

    String payload = objectMapper.writeValueAsString(matchDetail);

    Match match = Match.from(
        matchId,
        info.queueId(),
        info.gameCreation(),
        info.gameVersion(),
        payload
    );

    matchRepository.save(match);
  }
}
