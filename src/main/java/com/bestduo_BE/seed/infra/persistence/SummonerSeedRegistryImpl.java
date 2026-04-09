package com.bestduo_BE.seed.infra.persistence;

import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.seed.application.port.SummonerSeedRegistry;
import com.bestduo_BE.common.infra.persistence.entity.Summoner;
import com.bestduo_BE.common.infra.persistence.repository.SummonerJpaRepository;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class SummonerSeedRegistryImpl implements SummonerSeedRegistry {

  private final SummonerJpaRepository repository;

  @Override
  @Transactional
  public boolean registerIfAbsent(String puuid, Tier tier, OffsetDateTime observedAt) {
    try {
      Summoner summoner = Summoner.create(puuid);
      if (tier != null) {
        summoner.observeTier(tier, observedAt);
      }
      repository.save(summoner);
      return true;
    } catch (DataIntegrityViolationException e) {
      return false; // already exists — tier 덮어쓰지 않음
    }
  }
}
