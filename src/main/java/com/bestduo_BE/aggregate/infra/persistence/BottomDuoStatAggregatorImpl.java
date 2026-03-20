package com.bestduo_BE.aggregate.infra.persistence;

import com.bestduo_BE.aggregate.application.port.BottomDuoStatAggregator;
import com.bestduo_BE.aggregate.infra.persistence.repository.BottomDuoStatAggregateJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BottomDuoStatAggregatorImpl implements BottomDuoStatAggregator {
  private final BottomDuoStatAggregateJpaRepository repository;

  @Override
  public int aggregateAll() {
    return repository.upsertAllFromRaw();
  }
}
