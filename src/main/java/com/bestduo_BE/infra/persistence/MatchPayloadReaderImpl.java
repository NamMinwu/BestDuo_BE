package com.bestduo_BE.infra.persistence;

import com.bestduo_BE.application.port.MatchPayloadReader;
import com.bestduo_BE.infra.persistence.repository.MatchJpaRepository;
import com.bestduo_BE.infra.riot.dto.RiotMatchDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class MatchPayloadReaderImpl implements MatchPayloadReader {

  private final MatchJpaRepository matchRepository;
  private final ObjectMapper objectMapper;

  @Override
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
