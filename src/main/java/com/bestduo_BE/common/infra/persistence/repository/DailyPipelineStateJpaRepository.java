package com.bestduo_BE.common.infra.persistence.repository;

import com.bestduo_BE.common.infra.persistence.entity.DailyPipelineState;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyPipelineStateJpaRepository extends JpaRepository<DailyPipelineState, Long> {

  Optional<DailyPipelineState> findByPipelineDate(LocalDate pipelineDate);

  /** 해당 날짜의 row 가 있으면 반환, 없으면 새로 만들어 저장 후 반환. */
  default DailyPipelineState getOrCreateForDate(LocalDate date) {
    return findByPipelineDate(date).orElseGet(() -> save(DailyPipelineState.create(date)));
  }
}
