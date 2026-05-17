package com.bestduo_BE.common.infra.persistence.repository;


import com.bestduo_BE.common.infra.persistence.entity.Match;
import com.bestduo_BE.common.infra.persistence.projection.MatchPayloadProjection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

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

  /**
   * Archive 경로 전용 projection 페이지네이션.
   * <p>{@link MatchPayloadProjection} 으로 받아 Hibernate L1 캐시에 엔티티가 등록되지 않도록 한다.
   * 5-tier 순회 HTTP 요청에서 OSIV 가 세션을 잡고 있어도 페이지가 GC 대상이 되어 OOM 을 피한다.
   */
  @Query(value = """
      select match_id as matchId, payload_json as payloadJson
      from match
      where collection_tier = :tier
        and split_part(game_version, '.', 1) || '.' || split_part(game_version, '.', 2) = :patch
        and match_id > :afterId
      order by match_id asc
      limit :pageSize
      """, nativeQuery = true)
  List<MatchPayloadProjection> findPayloadPageByTierAndPatch(
      @Param("tier") String tier,
      @Param("patch") String patch,
      @Param("afterId") String afterId,
      @Param("pageSize") int pageSize
  );

  /**
   * match 테이블에 실제로 존재하는 distinct patch 목록.
   * <p>archive cleanup endpoint 에서 "최신 N 개" protected 목록을 계산하기 위함.
   */
  @Query(value = """
      select distinct split_part(game_version, '.', 1) || '.' || split_part(game_version, '.', 2)
      from match
      """, nativeQuery = true)
  List<String> findDistinctPatches();

  /**
   * (tier, patch) 조건의 match 행을 일괄 삭제한다.
   * <p>R2 archive 가 끝난 후 디스크 회수용. 자체 트랜잭션이므로 호출 측은 비-@Transactional 이어도 안전.
   */
  @Modifying
  @Transactional
  @Query(value = """
      delete from match
      where collection_tier = :tier
        and split_part(game_version, '.', 1) || '.' || split_part(game_version, '.', 2) = :patch
      """, nativeQuery = true)
  int deleteByTierAndPatch(@Param("tier") String tier, @Param("patch") String patch);
}
