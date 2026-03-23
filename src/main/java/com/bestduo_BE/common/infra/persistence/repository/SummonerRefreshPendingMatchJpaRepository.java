package com.bestduo_BE.common.infra.persistence.repository;

import com.bestduo_BE.common.infra.persistence.entity.SummonerRefreshPendingMatch;
import com.bestduo_BE.common.infra.persistence.entity.SummonerRefreshPendingMatchId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SummonerRefreshPendingMatchJpaRepository
    extends JpaRepository<SummonerRefreshPendingMatch, SummonerRefreshPendingMatchId> {

  boolean existsByIdPuuid(String puuid);

  List<SummonerRefreshPendingMatch> findByIdPuuidOrderByResponseIndexAsc(String puuid);

  List<SummonerRefreshPendingMatch> findByIdMatchId(String matchId);
}
