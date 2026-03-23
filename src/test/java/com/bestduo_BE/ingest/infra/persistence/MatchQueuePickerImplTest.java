package com.bestduo_BE.ingest.infra.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.bestduo_BE.ingest.application.port.MatchQueuePicker.MatchQueueItem;
import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.persistence.entity.MatchQueue;
import com.bestduo_BE.common.infra.persistence.repository.MatchQueueJpaRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MatchQueuePickerImplTest {

  @Mock
  private MatchQueueJpaRepository repository;

  private MatchQueuePickerImpl picker;

  @BeforeEach
  void setUp() {
    picker = new MatchQueuePickerImpl(repository);
  }

  @Test
  void pickMapsEntitiesToItems() {
    MatchQueue ready = MatchQueue.newReady("m-1", Tier.EMERALD.name(), 20);
    MatchQueue retry = MatchQueue.newReady("m-2", Tier.SILVER.name(), 30);
    given(repository.pickReadyOrRetry(2)).willReturn(List.of(ready, retry));

    List<MatchQueueItem> items = picker.pick(2);

    verify(repository).pickReadyOrRetry(2);
    assertThat(items).containsExactly(
        new MatchQueueItem("m-1", Tier.EMERALD, 20),
        new MatchQueueItem("m-2", Tier.SILVER, 30)
    );
  }

  @Test
  void markRunningUpdatesStatus() {
    MatchQueue mq = MatchQueue.newReady("running", Tier.GOLD.name(), 10);
    given(repository.findById("running")).willReturn(Optional.of(mq));

    picker.markRunning("running");

    verify(repository).findById("running");
    assertThat(mq.getStatus()).isEqualTo("RUNNING");
  }

  @Test
  void markDoneClearsLock() {
    MatchQueue mq = MatchQueue.newReady("done", Tier.BRONZE.name(), 5);
    given(repository.findById("done")).willReturn(Optional.of(mq));

    picker.markDone("done");

    assertThat(mq.getStatus()).isEqualTo("DONE");
  }

  @Test
  void markErrorRecordsFailureMessage() {
    MatchQueue mq = MatchQueue.newReady("err", Tier.PLATINUM.name(), 1);
    given(repository.findById("err")).willReturn(Optional.of(mq));

    picker.markError("err", "boom");

    assertThat(mq.getStatus()).isEqualTo("ERROR");
    assertThat(mq.getLastError()).isEqualTo("boom");
  }

  @Test
  void markRunningThrowsWhenMatchNotFound() {
    given(repository.findById("missing")).willReturn(Optional.empty());

    assertThrows(IllegalStateException.class, () -> picker.markRunning("missing"));
  }
}
