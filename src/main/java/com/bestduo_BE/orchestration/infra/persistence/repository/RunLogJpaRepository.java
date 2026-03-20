package com.bestduo_BE.orchestration.infra.persistence.repository;

import com.bestduo_BE.orchestration.infra.persistence.entity.RunLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RunLogJpaRepository extends JpaRepository<RunLog, Long> {

}
