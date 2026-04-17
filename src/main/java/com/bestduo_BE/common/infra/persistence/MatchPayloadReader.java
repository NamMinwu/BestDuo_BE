package com.bestduo_BE.common.infra.persistence;

import com.bestduo_BE.common.infra.persistence.repository.MatchJpaRepository;
import com.bestduo_BE.common.infra.riot.dto.RiotMatchDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class MatchPayloadReader {

  private final MatchJpaRepository matchRepository;
  private final ObjectMapper objectMapper;

  public RiotMatchDto read(String matchId) {
    var match = matchRepository.findById(matchId)
        .orElseThrow(() -> new IllegalStateException("Match not found: " + matchId));

    try {
      return objectMapper.readValue(match.getPayloadJson(), RiotMatchDto.class);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to parse match payload_json for " + matchId, e);
    }
  }
}
