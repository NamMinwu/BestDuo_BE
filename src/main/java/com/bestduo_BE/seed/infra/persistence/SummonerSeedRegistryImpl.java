package com.bestduo_BE.seed.infra.persistence;

import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.seed.application.port.SummonerSeedRegistry;
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
  public void upsertSeeded(String puuid, Tier tier, OffsetDateTime seededAt) {
    String tierName = tier != null ? tier.name() : null;
    repository.upsertSeeded(puuid, tierName, seededAt);
  }
}
