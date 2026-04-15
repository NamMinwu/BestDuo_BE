package com.bestduo_BE.leagueentry.infra.persistence;

import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.leagueentry.application.port.SummonerSeedRegistry;
import com.bestduo_BE.common.infra.persistence.repository.SummonerJpaRepository;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class SummonerSeedRegistryImpl implements SummonerSeedRegistry {

  private final SummonerJpaRepository repository;

  @Override
  @Transactional
  public void upsertLeagueEntry(String puuid, Tier tier, OffsetDateTime fetchedAt) {
    String tierName = tier != null ? tier.name() : null;
    repository.upsertLeagueEntry(puuid, tierName, fetchedAt);
  }
}
