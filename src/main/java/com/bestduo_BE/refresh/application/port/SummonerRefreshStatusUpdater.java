package com.bestduo_BE.refresh.application.port;

import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.persistence.entity.Summoner;
import java.time.OffsetDateTime;

public interface SummonerRefreshStatusUpdater {

  Summoner findOrCreate(String puuid);

  void syncResolvedTier(String puuid, Tier tier, OffsetDateTime observedAt);

  void syncRefreshCursor(String puuid, Long newLastMatchStartTime);
}
