package com.bestduo_BE.infra.persistence.repository;

import com.bestduo_BE.infra.persistence.entity.SessionRunLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionRunLogJpaRepository extends JpaRepository<SessionRunLog, Long> {

}
