package com.bestduo_BE.infra.persistence.repository;

import com.bestduo_BE.infra.persistence.entity.MatchQueue;
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

  @Modifying
  @Query(value = """
      update match_queue
      set status = 'READY', locked_at = null, updated_at = now()
      where status = 'RUNNING' and locked_at <= now() - interval '10 minutes'
      """, nativeQuery = true)
  int recoverStaleRunning();
}
