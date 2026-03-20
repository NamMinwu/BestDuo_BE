package com.bestduo_BE.aggregate.infra.persistence;

import com.bestduo_BE.aggregate.application.port.BottomDuoMatchupAggregator;
import com.bestduo_BE.aggregate.infra.persistence.repository.BottomDuoMatchupAggregateJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
public class BottomDuoMatchupAggregatorImpl implements BottomDuoMatchupAggregator {

  private final BottomDuoMatchupAggregateJpaRepository repository;

  @Override
  public int aggregateAll() {
    return repository.upsertAllFromRaw();
  }
}
