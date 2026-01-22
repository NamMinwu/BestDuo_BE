package com.bestduo_BE.infra.persistence;

import com.bestduo_BE.application.port.SummonerExpandQueue;
import com.bestduo_BE.infra.persistence.entity.Summoner;
import com.bestduo_BE.infra.persistence.repository.SummonerJpaRepository;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class SummonerExpandQueueImpl implements SummonerExpandQueue {

  private final SummonerJpaRepository repository;

  @Override
  public List<String> findReadyPuuds(int limit) {
    Pageable page = PageRequest.of(0, limit);
    return repository.findByExpandStatusOrderByUpdatedAtAsc("READY", page)
        .stream()
        .map(Summoner::getPuuid)
        .toList();
  }

  @Override
  @Transactional
  public void markExpandRunning(String puuid) {
    repository.findById(puuid).ifPresent(Summoner::markExpandRunning);
  }

  @Override
  @Transactional
  public void markExpandDone(String puuid) {
    repository.findById(puuid).ifPresent(Summoner::markExpandDone);
  }

  @Override
  @Transactional
  public void markExpandError(String puuid) {
    repository.findById(puuid).ifPresent(Summoner::markExpandError);
  }

  @Override
  @Transactional
  public boolean registerIfAbsent(String puuid) {
    return repository.insertReadyIfAbsent(puuid, OffsetDateTime.now()) > 0;
  }
}
