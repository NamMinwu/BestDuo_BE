package com.bestduo_BE.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
  private static final int LAST_ERROR_MAX_LENGTH = 255;

  @Id
  @Column(name = "match_id", nullable = false)
  private String matchId;

  @Column(name = "status", nullable = false)
  private String status; // READY/RUNNING/DONE/ERROR

  @Column(name = "priority", nullable = false)
  private int priority;

  @Column(name = "collection_tier", nullable = false)
  private String collectionTier; // Tier enum name

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

  public static MatchQueue newReady(String matchId, String collectionTier, int priority) {
    OffsetDateTime now = OffsetDateTime.now();
    return MatchQueue.builder()
        .matchId(matchId)
        .status("READY")
        .priority(priority)
        .collectionTier(collectionTier)
        .retryCount(0)
        .lastError(null)
        .lockedAt(null)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }

  public void markRunning() {
    this.status = "RUNNING";
    this.lockedAt = OffsetDateTime.now();
    this.updatedAt = OffsetDateTime.now();
  }

  public void markDone() {
    this.status = "DONE";
    this.lockedAt = null;
    this.updatedAt = OffsetDateTime.now();
  }

  public void markError(String message) {
    this.status = "ERROR";
    this.retryCount += 1;
    this.lastError = truncate(message, LAST_ERROR_MAX_LENGTH);
    this.lockedAt = null;
    this.updatedAt = OffsetDateTime.now();
  }

  private static String truncate(String message, int maxLength) {
    if (message == null || message.length() <= maxLength) {
      return message;
    }
    return message.substring(0, maxLength);
  }
}
