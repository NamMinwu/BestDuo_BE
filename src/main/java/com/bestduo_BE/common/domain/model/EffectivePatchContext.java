package com.bestduo_BE.common.domain.model;

/**
 * match ID 수집 시 사용할 "유효 패치" 컨텍스트.
 *
 * <p>새 패치 출시 후 {@code PATCH_GRACE_PERIOD_DAYS}일 미만이면 grace period로 간주해
 * 이전 패치를 effective patch로 사용하고 {@code endTimeEpochSeconds}를 설정한다.
 *
 * @param patch                 effective patch 문자열 (e.g., "16.7")
 * @param startTimeEpochSeconds Riot API startTime 파라미터 (epoch seconds)
 * @param endTimeEpochSeconds   Riot API endTime 파라미터 (epoch seconds); grace period가 아니면 null
 */
public record EffectivePatchContext(
    String patch,
    long startTimeEpochSeconds,
    Long endTimeEpochSeconds
) {

  /** grace period 중이면 true (endTime이 설정됨) */
  public boolean isInGracePeriod() {
    return endTimeEpochSeconds != null;
  }
}
