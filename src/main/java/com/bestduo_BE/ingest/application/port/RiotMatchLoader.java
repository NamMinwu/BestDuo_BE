package com.bestduo_BE.ingest.application.port;

import com.bestduo_BE.common.infra.riot.dto.RiotMatchDto;

public interface RiotMatchLoader {
  RiotMatchDto loadMatch(String matchId);
}
