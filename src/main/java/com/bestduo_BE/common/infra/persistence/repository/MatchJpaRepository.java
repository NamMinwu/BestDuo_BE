package com.bestduo_BE.common.infra.persistence.repository;


import com.bestduo_BE.common.infra.persistence.entity.Match;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchJpaRepository extends JpaRepository<Match, String> {}
