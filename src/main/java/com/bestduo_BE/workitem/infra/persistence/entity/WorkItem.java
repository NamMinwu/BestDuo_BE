package com.bestduo_BE.workitem.infra.persistence.entity;

import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.workitem.domain.model.WorkItemStatus;
import com.bestduo_BE.workitem.domain.model.WorkItemType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "work_item", indexes = {
    @Index(name = "idx_work_item_status_priority", columnList = "status, priority, created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class WorkItem {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private WorkItemType type;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private WorkItemStatus status;

  @Column(name = "coverage_bucket_id", nullable = false)
  private Long coverageBucketId;

  @Column(length = 2000)
  private String payload;

  @Column(nullable = false)
  private String patch;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Tier tier;

  @Column(nullable = false)
  private int priority;

  @Column(name = "batch_limit")
  private Integer batchLimit;

  @Column(name = "retry_count", nullable = false)
  private int retryCount;

  @Column(name = "last_error", length = 512)
  private String lastError;

  @Column(name = "locked_at")
  private OffsetDateTime lockedAt;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  public static WorkItem pending(
      Long coverageBucketId,
      String patch,
      Tier tier,
      WorkItemType type,
      int priority,
      Integer batchLimit,
      String payload
  ) {
    OffsetDateTime now = OffsetDateTime.now();
    return WorkItem.builder()
        .coverageBucketId(coverageBucketId)
        .patch(patch)
        .tier(tier)
        .type(type)
        .status(WorkItemStatus.PENDING)
        .priority(priority)
        .batchLimit(batchLimit)
        .payload(payload)
        .retryCount(0)
        .lastError(null)
        .lockedAt(null)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }

  public void markRunning() {
    this.status = WorkItemStatus.RUNNING;
    this.lockedAt = OffsetDateTime.now();
    this.updatedAt = OffsetDateTime.now();
  }

  public void markDone() {
    this.status = WorkItemStatus.DONE;
    this.lockedAt = null;
    this.lastError = null;
    this.updatedAt = OffsetDateTime.now();
  }

  public void markError(String errorMessage) {
    this.status = WorkItemStatus.ERROR;
    this.lockedAt = null;
    this.retryCount += 1;
    this.lastError = errorMessage == null ? null : errorMessage.substring(0, Math.min(errorMessage.length(), 512));
    this.updatedAt = OffsetDateTime.now();
  }

  public void markPending() {
    this.status = WorkItemStatus.PENDING;
    this.lockedAt = null;
    this.updatedAt = OffsetDateTime.now();
  }
}
