package com.bestduo_BE.leagueentry.application.port;

import com.bestduo_BE.common.domain.model.Tier;
import java.time.OffsetDateTime;

public interface SummonerSeedRegistry {

  /** 이미 존재하면 leagueEntryFetchedAt을 갱신, 없으면 새로 등록 후 leagueEntryFetchedAt 설정. */
  void upsertLeagueEntry(String puuid, Tier tier, OffsetDateTime fetchedAt);
}
