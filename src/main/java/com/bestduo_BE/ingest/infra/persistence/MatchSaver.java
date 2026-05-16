package com.bestduo_BE.ingest.infra.persistence;

import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.persistence.entity.Match;
import com.bestduo_BE.common.infra.persistence.repository.MatchJpaRepository;
import com.bestduo_BE.common.infra.riot.dto.InfoDto;
import com.bestduo_BE.common.infra.riot.dto.RiotMatchDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class MatchSaver {

  private final MatchJpaRepository matchRepository;
  private final ObjectMapper objectMapper;

  public void save(String matchId, RiotMatchDto matchDetail, Tier collectionTier) {
    InfoDto info = matchDetail.info();

    String payload = objectMapper.writeValueAsString(matchDetail);

    Match match = Match.from(
        matchId,
        info.queueId(),
        info.gameCreation(),
        info.gameVersion(),
        collectionTier,
        payload
    );

    matchRepository.save(match);
  }
}
