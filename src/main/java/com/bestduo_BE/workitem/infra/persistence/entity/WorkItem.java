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

  @Column(nullable = false)
  private String patch;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Tier tier;

  @Column(nullable = false)
  private int priority;

  @Column(name = "batch_limit")
  private Integer batchLimit;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  public static WorkItem ready(Long coverageBucketId, String patch, Tier tier, WorkItemType type, int priority, Integer batchLimit) {
    OffsetDateTime now = OffsetDateTime.now();
    return WorkItem.builder()
        .coverageBucketId(coverageBucketId)
        .patch(patch)
        .tier(tier)
        .type(type)
        .status(WorkItemStatus.READY)
        .priority(priority)
        .batchLimit(batchLimit)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }

  public void markRunning() {
    this.status = WorkItemStatus.RUNNING;
    this.updatedAt = OffsetDateTime.now();
  }

  public void markDone() {
    this.status = WorkItemStatus.DONE;
    this.updatedAt = OffsetDateTime.now();
  }

  public void markError() {
    this.status = WorkItemStatus.ERROR;
    this.updatedAt = OffsetDateTime.now();
  }
}
