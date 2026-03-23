package com.bestduo_BE.seed.infra.persistence;

import com.bestduo_BE.seed.application.port.SummonerSeedRegistry;
import com.bestduo_BE.common.infra.persistence.entity.Summoner;
import com.bestduo_BE.common.infra.persistence.repository.SummonerJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class SummonerSeedRegistryImpl implements SummonerSeedRegistry {

  private final SummonerJpaRepository repository;

  @Override
  public boolean registerIfAbsent(String puuid) {
    try {
      repository.save(Summoner.create(puuid));
      return true;
    } catch (DataIntegrityViolationException e) {
      return false; // already exists
    }
  }
}
