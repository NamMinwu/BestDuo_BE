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
    MatchQueue ready1 = match("m-1", Tier.GOLD, 1, "15.23");
    MatchQueue ready2 = match("m-2", Tier.SILVER, 2, "15.23");
    MatchQueue error = match("m-3", Tier.BRONZE, 3, "15.23");
    given(repository.pickReadyAndLock(3, null)).willReturn(List.of(ready1, ready2));
    given(repository.pickRetryableErrorAndLock(1, 2, 5, null)).willReturn(List.of(error));

    List<Item> items = coordinator.pickAndLock(3, 2, 5);

    verify(repository).pickReadyAndLock(3, null);
    verify(repository).pickRetryableErrorAndLock(1, 2, 5, null);
    assertThat(items).containsExactly(
        new Item("m-1", Tier.GOLD, 1, "15.23"),
        new Item("m-2", Tier.SILVER, 2, "15.23"),
        new Item("m-3", Tier.BRONZE, 3, "15.23")
    );
  }

  @Test
  void skipsRetryableErrorsWhenReadyFillLimit() {
    MatchQueue ready1 = match("m-1", Tier.EMERALD, 1, "15.23");
    MatchQueue ready2 = match("m-2", Tier.EMERALD, 2, "15.23");
    given(repository.pickReadyAndLock(2, null)).willReturn(List.of(ready1, ready2));

    List<Item> items = coordinator.pickAndLock(2, 2, 5);

    verify(repository).pickReadyAndLock(2, null);
    verify(repository, never()).pickRetryableErrorAndLock(anyInt(), anyInt(), anyInt(), org.mockito.ArgumentMatchers.<String>any());
    assertThat(items).containsExactly(
        new Item("m-1", Tier.EMERALD, 1, "15.23"),
        new Item("m-2", Tier.EMERALD, 2, "15.23")
    );
  }

  @Test
  void pickAndLockFiltersByRequestedTier() {
    MatchQueue ready = match("m-gold", Tier.GOLD, 1, "15.23");
    given(repository.pickReadyAndLock(2, "GOLD")).willReturn(List.of(ready));
    given(repository.pickRetryableErrorAndLock(1, 2, 5, "GOLD")).willReturn(List.of());

    List<Item> items = coordinator.pickAndLock(2, 2, 5, Tier.GOLD);

    verify(repository).pickReadyAndLock(2, "GOLD");
    verify(repository).pickRetryableErrorAndLock(1, 2, 5, "GOLD");
    assertThat(items).containsExactly(new Item("m-gold", Tier.GOLD, 1, "15.23"));
  }

  @Test
  void markDoneUpdatesEntityStatus() {
    MatchQueue mq = match("done", Tier.GOLD, 1, "15.23");
    given(repository.findById("done")).willReturn(Optional.of(mq));

    coordinator.markDone("done");

    verify(repository).findById("done");
    assertThat(mq.getStatus()).isEqualTo("DONE");
  }

  @Test
  void markErrorRegistersFailureMessage() {
    MatchQueue mq = match("err", Tier.GOLD, 1, "15.23");
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

  private MatchQueue match(String matchId, Tier tier, int priority, String patch) {
    OffsetDateTime now = OffsetDateTime.now();
    return MatchQueue.builder()
        .matchId(matchId)
        .status("READY")
        .priority(priority)
        .collectionTier(tier.name())
        .patch(patch)
        .retryCount(0)
        .lastError(null)
        .lockedAt(null)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }
}
