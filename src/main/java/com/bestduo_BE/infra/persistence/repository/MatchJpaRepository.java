package com.bestduo_BE.infra.persistence.repository;


import com.bestduo_BE.infra.persistence.entity.Match;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchJpaRepository extends JpaRepository<Match, String> {}
