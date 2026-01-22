package com.bestduo_BE.infra.persistence;

import com.bestduo_BE.application.port.BottomDuoRawSaver;
import com.bestduo_BE.domain.model.BottomDuoRaw;
import com.bestduo_BE.infra.persistence.entity.BottomDuoRawEntity;
import com.bestduo_BE.infra.persistence.repository.BottomDuoRawJpaRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class BottomDuoRawSaverImpl implements BottomDuoRawSaver {

  private final BottomDuoRawJpaRepository repository;

  @Override
  public void saveAllIdempotent(List<BottomDuoRaw> raws) {
    if (raws == null || raws.isEmpty()) return;

    for (BottomDuoRaw raw : raws) {
      try {
        repository.save(BottomDuoRawEntity.fromDomain(raw));
      } catch (DataIntegrityViolationException e) {
        log.error(e.getMessage());
        // PK(match_id, team_id) 중복 → 이미 저장됨 → 멱등 처리
      }
    }
  }
}