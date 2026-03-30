package com.bestduo_BE.refresh.infra.persistence;

import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.refresh.application.port.SummonerRefreshStatusUpdater;
import com.bestduo_BE.common.infra.persistence.entity.Summoner;
import com.bestduo_BE.common.infra.persistence.repository.SummonerJpaRepository;
import java.time.OffsetDateTime;
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
        .orElseGet(() -> repository.save(Summoner.create(puuid)));
  }

  @Override
  @Transactional
  public void syncResolvedTier(String puuid, Tier tier, OffsetDateTime observedAt) {
    repository.updateTierMetadata(puuid, tier == null ? null : tier.name(), observedAt);
  }

  @Override
  @Transactional
  public void syncRefreshCursor(String puuid, Long newLastMatchStartTime) {
    repository.advanceLastMatchStartTime(puuid, newLastMatchStartTime);
  }
}
