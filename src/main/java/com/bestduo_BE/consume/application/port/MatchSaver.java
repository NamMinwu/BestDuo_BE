package com.bestduo_BE.consume.application.port;

import com.bestduo_BE.common.infra.riot.dto.RiotMatchDto;

public interface MatchSaver {
  void save(String matchId, RiotMatchDto matchDetail);
}
