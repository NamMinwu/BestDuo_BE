package com.bestduo_BE.seed.application.port;

import com.bestduo_BE.common.domain.model.Tier;
import java.time.OffsetDateTime;

public interface SummonerSeedRegistry {
  boolean registerIfAbsent(String puuid, Tier tier, OffsetDateTime observedAt);

  /** 이미 존재하면 seeded_at을 갱신, 없으면 새로 등록 후 seeded_at 설정. */
  void upsertSeeded(String puuid, Tier tier, OffsetDateTime seededAt);
}
