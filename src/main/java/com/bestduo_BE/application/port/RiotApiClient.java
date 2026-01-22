package com.bestduo_BE.application.port;

import com.bestduo_BE.domain.model.Division;
import com.bestduo_BE.domain.model.Tier;
import com.bestduo_BE.infra.riot.dto.LeagueEntry;
import com.bestduo_BE.infra.riot.dto.RiotMatchDto;
import java.util.List;

public interface RiotApiClient {
  List<String> loadMatchIdsByPuuid(String puuid, int count);
  RiotMatchDto loadMatch(String matchId);
}
