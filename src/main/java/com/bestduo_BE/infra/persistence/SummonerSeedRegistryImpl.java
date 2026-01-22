package com.bestduo_BE.infra.persistence;

import com.bestduo_BE.application.port.SummonerSeedRegistry;
import com.bestduo_BE.infra.persistence.entity.Summoner;
import com.bestduo_BE.infra.persistence.repository.SummonerJpaRepository;
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
      repository.save(Summoner.newReady(puuid));
      return true;
    } catch (DataIntegrityViolationException e) {
      return false; // already exists
    }
  }

  @Override
  @Transactional
  public void markSeedRunning(String puuid) {
    repository.findById(puuid).ifPresent(Summoner::markSeedRunning);
  }

  @Override
  @Transactional
  public void markSeedDone(String puuid) {
    repository.findById(puuid).ifPresent(Summoner::markSeedDone);
  }

  @Override
  @Transactional
  public void markSeedError(String puuid) {
    repository.findById(puuid).ifPresent(Summoner::markSeedError);
  }
}
