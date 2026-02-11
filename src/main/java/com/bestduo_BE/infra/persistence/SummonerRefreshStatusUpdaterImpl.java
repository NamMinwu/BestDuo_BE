package com.bestduo_BE.infra.persistence;

import com.bestduo_BE.application.port.SummonerRefreshStatusUpdater;
import com.bestduo_BE.infra.persistence.entity.Summoner;
import com.bestduo_BE.infra.persistence.repository.SummonerJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class SummonerRefreshStatusUpdaterImpl implements SummonerRefreshStatusUpdater {

  private final SummonerJpaRepository repository;

  @Override
  @Transactional
  public Summoner findOrCreate(String puuid) {
    return repository.findById(puuid)
        .orElseGet(() -> repository.save(Summoner.newReady(puuid)));
  }

  @Override
  @Transactional
  public void markRefreshRunning(String puuid) {
    repository.findById(puuid).ifPresent(Summoner::markRefreshRunning);
  }

  @Override
  @Transactional
  public void markRefreshDone(String puuid, Long newLastMatchStartTime) {
    repository.findById(puuid).ifPresent(s -> s.markRefreshDone(newLastMatchStartTime));
  }

  @Override
  @Transactional
  public void markRefreshError(String puuid) {
    repository.findById(puuid).ifPresent(Summoner::markRefreshError);
  }
}
