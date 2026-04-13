package com.bestduo_BE.common.infra.persistence.repository;

import com.bestduo_BE.common.infra.persistence.entity.DailyPipelineState;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyPipelineStateJpaRepository extends JpaRepository<DailyPipelineState, Long> {

  Optional<DailyPipelineState> findByPipelineDate(LocalDate pipelineDate);
}
