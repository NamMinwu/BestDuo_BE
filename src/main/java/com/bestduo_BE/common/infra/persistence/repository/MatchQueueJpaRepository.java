package com.bestduo_BE.common.infra.persistence.repository;

import com.bestduo_BE.common.infra.persistence.entity.MatchQueue;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchQueueJpaRepository extends JpaRepository<MatchQueue, String> {
  @Query(value = """
      select mq.*
      from match_queue mq
      where
        (mq.status = 'READY')
        or (mq.status = 'ERROR' and mq.updated_at <= now() - interval '2 minutes')
      order by
        case when mq.status = 'ERROR' then 0 else 1 end,
        mq.priority asc,
        mq.updated_at asc
      limit :limit
      """, nativeQuery = true)
  List<MatchQueue> pickReadyOrRetry(@Param("limit") int limit);

  // 1) RUNNING stale 복구: lockedAt 오래된 RUNNING을 READY로 되돌림
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(value = """
    update match_queue
    set status = 'READY',
        locked_at = null,
        updated_at = now()
    where status = 'RUNNING'
      and locked_at is not null
      and locked_at <= now() - (:staleMinutes || ' minutes')::interval
    """, nativeQuery = true)
  int recoverStaleRunning(@Param("staleMinutes") int staleMinutes);

  // 2) READY만 먼저 pick&lock (RUNNING으로 바꾸고 반환)
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(value = """
    with candidates as (
      select mq.match_id
      from match_queue mq
      where mq.status = 'READY'
      order by mq.priority asc, mq.updated_at asc
      limit :limit
    )
    update match_queue mq
    set status = 'RUNNING',
        locked_at = now(),
        updated_at = now()
    where mq.match_id in (select match_id from candidates)
      and mq.status = 'READY'
    returning mq.*
    """, nativeQuery = true)
  List<MatchQueue> pickReadyAndLock(@Param("limit") int limit);

  // 3) ERROR 중 재시도 가능한 것만 pick&lock (cooldown + maxRetry)
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(value = """
    with candidates as (
      select mq.match_id
      from match_queue mq
      where mq.status = 'ERROR'
        and mq.retry_count < :maxRetry
        and mq.updated_at <= now() - (:cooldownMinutes || ' minutes')::interval
      order by mq.priority asc, mq.updated_at asc
      limit :limit
    )
    update match_queue mq
    set status = 'RUNNING',
        locked_at = now(),
        updated_at = now()
    where mq.match_id in (select match_id from candidates)
      and mq.status = 'ERROR'
      and mq.retry_count < :maxRetry
      and mq.updated_at <= now() - (:cooldownMinutes || ' minutes')::interval
    returning mq.*
    """, nativeQuery = true)
  List<MatchQueue> pickRetryableErrorAndLock(
      @Param("limit") int limit,
      @Param("maxRetry") int maxRetry,
      @Param("cooldownMinutes") int cooldownMinutes
  );

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(value = """
    update match_queue
    set status = 'READY',
        locked_at = null,
        updated_at = now()
    where match_id = :matchId
      and status = 'RUNNING'
    """, nativeQuery = true)
  int unlockToReady(@Param("matchId") String matchId);
}
