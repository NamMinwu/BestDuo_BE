package com.bestduo_BE.orchestration.infra.persistence.repository;

import com.bestduo_BE.orchestration.infra.persistence.entity.ExecutionLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExecutionLogJpaRepository extends JpaRepository<ExecutionLog, Long> {

}
