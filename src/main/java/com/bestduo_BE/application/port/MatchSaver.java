package com.bestduo_BE.application.port;

import com.bestduo_BE.infra.riot.dto.RiotMatchDto;

public interface MatchSaver {
  void save(String matchId, RiotMatchDto matchDetail);
}
