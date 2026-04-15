package com.bestduo_BE.ingest.infra.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.persistence.entity.MatchQueue;
import com.bestduo_BE.common.infra.persistence.repository.MatchQueueJpaRepository;
import com.bestduo_BE.ingest.application.port.MatchQueueDispatcher;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Phase 5: patch+tier 우선순위 pickAndLockWithPriority 검증.
 */
@ExtendWith(MockitoExtension.class)
class MatchQueueDispatcherPhase5Test {

  @Mock
  private MatchQueueJpaRepository repo;

  private MatchQueueDispatcherImpl dispatcher;

  @BeforeEach
  void setUp() {
    dispatcher = new MatchQueueDispatcherImpl(repo);
  }

  @Test
  @DisplayName("pickAndLockWithPriority는 currentPatch를 포함한 쿼리를 호출한다")
  void pickAndLockWithPriority_callsRepoWithCurrentPatch() {
    given(repo.pickReadyWithPriorityAndLock(anyInt(), any(), anyString())).willReturn(List.of());
    given(repo.pickRetryableErrorAndLock(anyInt(), anyInt(), anyInt(), any())).willReturn(List.of());

    dispatcher.pickAndLockWithPriority(5, 2, 10, null, "15.23");

    verify(repo).pickReadyWithPriorityAndLock(5, null, "15.23");
  }

  @Test
  @DisplayName("pickAndLockWithPriority 결과를 Item으로 변환한다")
  void pickAndLockWithPriority_convertsToItems() {
    MatchQueue mq = MatchQueue.newReady("match-1", Tier.DIAMOND, 50, "15.23");
    given(repo.pickReadyWithPriorityAndLock(anyInt(), any(), anyString()))
        .willReturn(List.of(mq));
    given(repo.pickRetryableErrorAndLock(anyInt(), anyInt(), anyInt(), any())).willReturn(List.of());

    List<MatchQueueDispatcher.Item> items = dispatcher.pickAndLockWithPriority(5, 2, 10, null, "15.23");

    assertThat(items).hasSize(1);
    assertThat(items.get(0).matchId()).isEqualTo("match-1");
    assertThat(items.get(0).tier()).isEqualTo(Tier.DIAMOND);
    assertThat(items.get(0).patch()).isEqualTo("15.23");
  }

  @Test
  @DisplayName("기존 pickAndLock 메서드는 영향받지 않는다")
  void legacyPickAndLock_unaffectedByPhase5Changes() {
    given(repo.pickReadyAndLock(anyInt(), any())).willReturn(List.of());
    given(repo.pickRetryableErrorAndLock(anyInt(), anyInt(), anyInt(), any())).willReturn(List.of());

    List<MatchQueueDispatcher.Item> items = dispatcher.pickAndLock(3, 2, 10, null);

    assertThat(items).isEmpty();
    verify(repo).pickReadyAndLock(3, null);
  }
}
