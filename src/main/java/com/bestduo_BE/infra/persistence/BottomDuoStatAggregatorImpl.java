package com.bestduo_BE.infra.persistence;

import com.bestduo_BE.application.port.BottomDuoStatAggregator;
import com.bestduo_BE.infra.persistence.repository.BottomDuoStatAggJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BottomDuoStatAggregatorImpl implements BottomDuoStatAggregator {
  private final BottomDuoStatAggJpaRepository repository;

  @Override
  public int aggregateAll() {
    return repository.upsertAllFromRaw();
  }
}
