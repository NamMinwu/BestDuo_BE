package com.bestduo_BE.common.infra.persistence.entity;

import com.bestduo_BE.common.domain.exception.IllegalStateTransitionException;
import com.bestduo_BE.common.domain.model.QueueStatus;
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
@Table(name = "match_queue")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class MatchQueue {
  public static final int LAST_ERROR_MAX_LENGTH = 255;

  @Id
  @Column(name = "match_id", nullable = false)
  private String matchId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private QueueStatus status;

  @Column(name = "priority", nullable = false)
  private int priority;

  @Enumerated(EnumType.STRING)
  @Column(name = "collection_tier", nullable = false)
  private Tier collectionTier;

  @Column(name = "patch")
  private String patch;

  @Column(name = "retry_count", nullable = false)
  private int retryCount;

  @Column(name = "last_error")
  private String lastError;

  @Column(name = "locked_at")
  private OffsetDateTime lockedAt;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  public static MatchQueue newReady(String matchId, Tier collectionTier, int priority, String patch) {
    OffsetDateTime now = OffsetDateTime.now();
    return MatchQueue.builder()
        .matchId(matchId)
        .status(QueueStatus.READY)
        .priority(priority)
        .collectionTier(collectionTier)
        .patch(patch)
        .retryCount(0)
        .lastError(null)
        .lockedAt(null)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }

  public void markRunning() {
    requireStatus(QueueStatus.READY);
    this.status = QueueStatus.RUNNING;
    this.lockedAt = OffsetDateTime.now();
    this.updatedAt = OffsetDateTime.now();
  }

  public void markDone() {
    requireStatus(QueueStatus.RUNNING);
    this.status = QueueStatus.DONE;
    this.lockedAt = null;
    this.updatedAt = OffsetDateTime.now();
  }

  public void markError(String message) {
    requireStatus(QueueStatus.RUNNING);
    this.status = QueueStatus.ERROR;
    this.retryCount += 1;
    this.lastError = truncate(message, LAST_ERROR_MAX_LENGTH);
    this.lockedAt = null;
    this.updatedAt = OffsetDateTime.now();
  }

  private void requireStatus(QueueStatus expected) {
    if (this.status != expected) {
      throw new IllegalStateTransitionException(
          "Expected status " + expected + " but was " + this.status + " for matchId=" + matchId);
    }
  }

  private static String truncate(String message, int maxLength) {
    if (message == null || message.length() <= maxLength) {
      return message;
    }
    return message.substring(0, maxLength);
  }
}
