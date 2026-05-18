package com.bestduo_BE.common.infra.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bestduo_BE.common.domain.exception.IllegalStateTransitionException;
import com.bestduo_BE.common.domain.model.QueueStatus;
import com.bestduo_BE.common.domain.model.Tier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MatchQueueTest {

  @Test
  @DisplayName("newReady — 패치 정보를 포함한 READY 상태 엔트리를 생성한다")
  void newReadyIncludesPatch() {
    MatchQueue queue = MatchQueue.newReady("match-1", Tier.EMERALD, "15.23");

    assertThat(queue.getMatchId()).isEqualTo("match-1");
    assertThat(queue.getCollectionTier()).isEqualTo(Tier.EMERALD);
    assertThat(queue.getPatch()).isEqualTo("15.23");
    assertThat(queue.getStatus()).isEqualTo(QueueStatus.READY);
  }

  @Test
  @DisplayName("markRunning — READY 상태가 아니면 IllegalStateTransitionException을 던진다")
  void markRunning_whenNotReady_throws() {
    MatchQueue queue = MatchQueue.newReady("match-1", Tier.EMERALD, "15.23");
    queue.markRunning(); // READY → RUNNING

    assertThatThrownBy(queue::markRunning)
        .isInstanceOf(IllegalStateTransitionException.class);
  }

  @Test
  @DisplayName("markDone — RUNNING 상태가 아니면 IllegalStateTransitionException을 던진다")
  void markDone_whenNotRunning_throws() {
    MatchQueue queue = MatchQueue.newReady("match-1", Tier.EMERALD, "15.23");

    assertThatThrownBy(queue::markDone)
        .isInstanceOf(IllegalStateTransitionException.class);
  }

  @Test
  @DisplayName("markError — RUNNING 상태가 아니면 IllegalStateTransitionException을 던진다")
  void markError_whenNotRunning_throws() {
    MatchQueue queue = MatchQueue.newReady("match-1", Tier.EMERALD, "15.23");

    assertThatThrownBy(() -> queue.markError("boom"))
        .isInstanceOf(IllegalStateTransitionException.class);
  }
}
