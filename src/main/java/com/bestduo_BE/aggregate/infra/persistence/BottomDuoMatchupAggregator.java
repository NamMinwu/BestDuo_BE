package com.bestduo_BE.aggregate.infra.persistence;

import com.bestduo_BE.aggregate.infra.persistence.repository.BottomDuoMatchupAggregateJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BottomDuoMatchupAggregator {

  private final BottomDuoMatchupAggregateJpaRepository repository;

  public int aggregateAll() {
    return repository.upsertAllFromRaw();
  }
}
