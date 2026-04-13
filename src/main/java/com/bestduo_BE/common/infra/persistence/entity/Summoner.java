package com.bestduo_BE.common.infra.persistence.entity;

import com.bestduo_BE.common.domain.model.Tier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "summoner")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Summoner {

  @Id
  @Column(name = "puuid", nullable = false)
  private String puuid;

  @Column(name = "last_match_start_time")
  private Long lastMatchStartTime; // epoch seconds

  @Enumerated(EnumType.STRING)
  @Column(name = "last_known_tier")
  private Tier lastKnownTier;

  @Column(name = "tier_observed_at")
  private OffsetDateTime tierObservedAt;

  @Column(name = "last_seen_patch")
  private String lastSeenPatch;

  @Column(name = "seeded_at")
  private OffsetDateTime seededAt;

  @Column(name = "match_ids_collected_at")
  private OffsetDateTime matchIdsCollectedAt;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  public static Summoner create(String puuid) {
    OffsetDateTime now = OffsetDateTime.now();
    return Summoner.builder()
        .puuid(puuid)
        .lastMatchStartTime(null)
        .lastKnownTier(null)
        .tierObservedAt(null)
        .lastSeenPatch(null)
        .seededAt(null)
        .matchIdsCollectedAt(null)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }

  public void markSeeded(OffsetDateTime seededAt) {
    this.seededAt = seededAt;
    this.updatedAt = OffsetDateTime.now();
  }

  public void markMatchIdsCollected(OffsetDateTime collectedAt) {
    this.matchIdsCollectedAt = collectedAt;
    this.updatedAt = OffsetDateTime.now();
  }

  /**
   * matchIds 수집 대기 상태인지 판단한다.
   * <ul>
   *   <li>seededAt이 null이면 Stage 1을 거치지 않은 summoner → 대상 아님</li>
   *   <li>matchIdsCollectedAt이 null이면 한 번도 수집 안 함 → 대상</li>
   *   <li>matchIdsCollectedAt이 seededAt 이전이면 재수집 필요 → 대상</li>
   * </ul>
   */
  public boolean needsMatchIdsCollection() {
    if (seededAt == null) {
      return false;
    }
    if (matchIdsCollectedAt == null) {
      return true;
    }
    return matchIdsCollectedAt.isBefore(seededAt);
  }

  public void observeTier(Tier tier, OffsetDateTime observedAt) {
    this.lastKnownTier = tier;
    this.tierObservedAt = observedAt;
    this.updatedAt = OffsetDateTime.now();
  }

  public void advanceLastMatchStartTime(Long newLastMatchStartTimeOrNull) {
    if (newLastMatchStartTimeOrNull != null
        && (this.lastMatchStartTime == null || this.lastMatchStartTime < newLastMatchStartTimeOrNull)) {
      this.lastMatchStartTime = newLastMatchStartTimeOrNull;
    }
    this.updatedAt = OffsetDateTime.now();
  }
}
