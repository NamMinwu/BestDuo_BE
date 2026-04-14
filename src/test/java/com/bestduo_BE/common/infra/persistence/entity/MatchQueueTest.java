package com.bestduo_BE.common.infra.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MatchQueueTest {

  @Test
  @DisplayName("newReady — 패치 정보를 포함한 READY 상태 엔트리를 생성한다")
  void newReadyIncludesPatch() {
    MatchQueue queue = MatchQueue.newReady("match-1", "EMERALD", 20, "15.23");

    assertThat(queue.getMatchId()).isEqualTo("match-1");
    assertThat(queue.getCollectionTier()).isEqualTo("EMERALD");
    assertThat(queue.getPriority()).isEqualTo(20);
    assertThat(queue.getPatch()).isEqualTo("15.23");
    assertThat(queue.getStatus()).isEqualTo("READY");
  }
}
