package com.bestduo_BE.ingest.application.port;

import com.bestduo_BE.common.domain.model.Tier;
import java.util.List;

public interface MatchQueueDispatcher {

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

  List<Item> pickAndLock(int limit, int maxRetry, int errorCooldownMinutes, Tier requestedTier);

  void markDone(String matchId);

  void markError(String matchId, String message);

  /**
   * Budget아/429 등 "실패가 니라 세션 종료" 상황:
   * RUNNING을 READY로 돌려 다음 세션에서 다시 처리되게 한다.
   */
  void unlockToReady(String matchId);

  /**
   * Phase 5: patch+tier 우선순위 READY pick&lock.
   * READY를 patch/tier 우선순위로 pick한 뒤 남는 자리를 ERROR 재시도로 채운다.
   */
  List<Item> pickAndLockWithPriority(int limit, int maxRetry, int errorCooldownMinutes, String currentPatch);

  record Item(String matchId, Tier tier, int priority, String patch) {}
}
