package com.bestduo_BE.common.application.port;

import com.bestduo_BE.common.infra.riot.dto.RiotMatchDto;

public interface MatchPayloadReader {
  RiotMatchDto read(String matchId);
}
