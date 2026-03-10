package com.bestduo_BE.infra.persistence.repository;

import com.bestduo_BE.application.port.MatchQueueErrorTop;
import com.bestduo_BE.application.port.MatchQueueRetryCount;
import com.bestduo_BE.application.port.MatchQueueStatusCount;
import com.bestduo_BE.infra.persistence.entity.MatchQueue;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface MatchQueueStatsJpaRepository extends Repository<MatchQueue, String> {
  @Query(value = """
    select mq.status as status, count(*) as cnt
    from match_queue mq
    group by mq.status
    """, nativeQuery = true)
  List<MatchQueueStatusCount> countByStatus();

  @Query(value = """
    select count(*) 
    from match_queue mq
    where mq.status = 'RUNNING'
      and mq.locked_at is not null
      and mq.locked_at <= now() - (:staleMinutes || ' minutes')::interval
    """, nativeQuery = true)
  long countStaleRunning(@Param("staleMinutes") int staleMinutes);

  @Query(value = """
    select mq.retry_count as retryCount, count(*) as cnt
    from match_queue mq
    where mq.status = 'ERROR'
    group by mq.retry_count
    order by mq.retry_count asc
    """, nativeQuery = true)
  List<MatchQueueRetryCount> countErrorByRetryCount();

  @Query(value = """
    select coalesce(mq.last_error, '(null)') as lastError, count(*) as cnt
    from match_queue mq
    where mq.status = 'ERROR'
    group by coalesce(mq.last_error, '(null)')
    order by count(*) desc
    limit :limit
    """, nativeQuery = true)
  List<MatchQueueErrorTop> topErrors(@Param("limit") int limit);

  @Query(value = """
    select min(mq.updated_at)
    from match_queue mq
    where mq.status = 'READY'
    """, nativeQuery = true)
  Instant findOldestReadyUpdatedAt();

  // (선택) 최근 N분 DONE 처리량(대략적인 throughput)
  @Query(value = """
    select count(*)
    from match_queue mq
    where mq.status = 'DONE'
      and mq.updated_at >= now() - (:minutes || ' minutes')::interval
    """, nativeQuery = true)
  long countDoneInLastMinutes(@Param("minutes") int minutes);
}
