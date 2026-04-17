package com.bestduo_BE.aggregate.infra.persistence;

import com.bestduo_BE.aggregate.infra.persistence.repository.BottomDuoStatAggregateJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BottomDuoStatAggregator {

  private final BottomDuoStatAggregateJpaRepository repository;

  public int aggregate(String patchVersion, String tier) {
    return repository.upsertFromRawByScope(patchVersion, tier);
  }
}
