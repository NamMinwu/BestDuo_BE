package com.bestduo_BE.infra.persistence.repository;

import com.bestduo_BE.infra.persistence.entity.Summoner;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SummonerJpaRepository extends JpaRepository<Summoner, String> {

}
