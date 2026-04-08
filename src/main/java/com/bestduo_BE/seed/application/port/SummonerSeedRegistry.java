package com.bestduo_BE.seed.application.port;

import com.bestduo_BE.common.domain.model.Tier;
import java.time.OffsetDateTime;

public interface SummonerSeedRegistry {
  boolean registerIfAbsent(String puuid, Tier tier, OffsetDateTime observedAt);
}
