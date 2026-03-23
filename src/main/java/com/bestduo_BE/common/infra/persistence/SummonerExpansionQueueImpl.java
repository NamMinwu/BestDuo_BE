package com.bestduo_BE.common.infra.persistence;

import com.bestduo_BE.common.application.port.SummonerExpansionQueue;
import com.bestduo_BE.common.infra.persistence.repository.SummonerJpaRepository;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class SummonerExpansionQueueImpl implements SummonerExpansionQueue {

  private final SummonerJpaRepository repository;

  @Override
  @Transactional
  public boolean registerIfAbsent(String puuid) {
    return repository.insertIfAbsent(puuid, OffsetDateTime.now()) > 0;
  }
}
