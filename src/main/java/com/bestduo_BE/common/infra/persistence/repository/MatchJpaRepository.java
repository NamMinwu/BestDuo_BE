package com.bestduo_BE.common.infra.persistence.repository;


import com.bestduo_BE.common.infra.persistence.entity.Match;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchJpaRepository extends JpaRepository<Match, String> {

  /**
   * (patch, collection_tier) 범위의 match 를 match_id keyset 기준으로 페이지네이션한다.
   * <p>aggregate from match 경로에서 payload_json 을 메모리로 흘려보내며 처리하기 위함.
   * <p>game_version 의 앞 두 토큰을 patch 로 매칭한다 (예: '15.23.1.2' → '15.23').
   */
  @Query(value = """
      select *
      from match
      where collection_tier = :tier
        and split_part(game_version, '.', 1) || '.' || split_part(game_version, '.', 2) = :patch
        and match_id > :afterId
      order by match_id asc
      limit :pageSize
      """, nativeQuery = true)
  List<Match> findPageByTierAndPatch(
      @Param("tier") String tier,
      @Param("patch") String patch,
      @Param("afterId") String afterId,
      @Param("pageSize") int pageSize
  );
}
