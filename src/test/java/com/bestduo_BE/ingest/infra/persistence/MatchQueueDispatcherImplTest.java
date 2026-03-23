package com.bestduo_BE.ingest.infra.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.bestduo_BE.ingest.application.port.MatchQueueDispatcher.Item;
import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.persistence.entity.MatchQueue;
import com.bestduo_BE.common.infra.persistence.repository.MatchQueueJpaRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MatchQueueDispatcherImplTest {

  @Mock
  private MatchQueueJpaRepository repository;

  private MatchQueueDispatcherImpl coordinator;

  @BeforeEach
  void setUp() {
    coordinator = new MatchQueueDispatcherImpl(repository);
  }

  @Test
  void recoverStaleRunningDelegatesToRepository() {
    given(repository.recoverStaleRunning(15)).willReturn(3);

    int recovered = coordinator.recoverStaleRunning(15);

    verify(repository).recoverStaleRunning(15);
    assertThat(recovered).isEqualTo(3);
  }

  @Test
  void pickAndLockPrefersReadyBeforeRetryableErrors() {
    MatchQueue ready1 = match("m-1", Tier.GOLD, 1);
    MatchQueue ready2 = match("m-2", Tier.SILVER, 2);
    MatchQueue error = match("m-3", Tier.BRONZE, 3);
    given(repository.pickReadyAndLock(3)).willReturn(List.of(ready1, ready2));
    given(repository.pickRetryableErrorAndLock(1, 2, 5)).willReturn(List.of(error));

    List<Item> items = coordinator.pickAndLock(3, 2, 5);

    verify(repository).pickReadyAndLock(3);
    verify(repository).pickRetryableErrorAndLock(1, 2, 5);
    assertThat(items).containsExactly(
        new Item("m-1", Tier.GOLD, 1),
        new Item("m-2", Tier.SILVER, 2),
        new Item("m-3", Tier.BRONZE, 3)
    );
  }

  @Test
  void skipsRetryableErrorsWhenReadyFillLimit() {
    MatchQueue ready1 = match("m-1", Tier.EMERALD, 1);
    MatchQueue ready2 = match("m-2", Tier.EMERALD, 2);
    given(repository.pickReadyAndLock(2)).willReturn(List.of(ready1, ready2));

    List<Item> items = coordinator.pickAndLock(2, 2, 5);

    verify(repository).pickReadyAndLock(2);
    verify(repository, never()).pickRetryableErrorAndLock(anyInt(), anyInt(), anyInt());
    assertThat(items).containsExactly(
        new Item("m-1", Tier.EMERALD, 1),
        new Item("m-2", Tier.EMERALD, 2)
    );
  }

  @Test
  void markDoneUpdatesEntityStatus() {
    MatchQueue mq = match("done", Tier.GOLD, 1);
    given(repository.findById("done")).willReturn(Optional.of(mq));

    coordinator.markDone("done");

    verify(repository).findById("done");
    assertThat(mq.getStatus()).isEqualTo("DONE");
  }

  @Test
  void markErrorRegistersFailureMessage() {
    MatchQueue mq = match("err", Tier.GOLD, 1);
    given(repository.findById("err")).willReturn(Optional.of(mq));

    coordinator.markError("err", "boom");

    verify(repository).findById("err");
    assertThat(mq.getStatus()).isEqualTo("ERROR");
    assertThat(mq.getLastError()).isEqualTo("boom");
  }

  @Test
  void unlockToReadyDelegatesToRepository() {
    coordinator.unlockToReady("match");

    verify(repository).unlockToReady("match");
  }

  private MatchQueue match(String matchId, Tier tier, int priority) {
    OffsetDateTime now = OffsetDateTime.now();
    return MatchQueue.builder()
        .matchId(matchId)
        .status("READY")
        .priority(priority)
        .collectionTier(tier.name())
        .retryCount(0)
        .lastError(null)
        .lockedAt(null)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }
}
