package com.bestduo_BE.application.port;

import com.bestduo_BE.domain.model.Tier;
import java.util.List;

public interface MatchQueueCoordinator {

  /**
   * RUNNING stuck 복구 (lockedAt 오래된 항목 READY로)
   * @return 복구된 개수
   */
  int recoverStaleRunning(int staleMinutes);

  /**
   * READY 우선 + 남는 자리 ERROR 재시도(쿨다운/리트라이 제한)로
   * "RUNNING으로 락 걸고" 아이템을 반환한다.
   */
  List<Item> pickAndLock(int limit, int maxRetry, int errorCooldownMinutes);

  void markDone(String matchId);

  void markError(String matchId, String message);

  /**
   * Budget/429 등 "실패가 아니라 세션 종료" 상황:
   * RUNNING을 READY로 돌려 다음 세션에서 다시 처리되게 한다.
   */
  void unlockToReady(String matchId);

  record Item(String matchId, Tier tier, int priority) {}
}
