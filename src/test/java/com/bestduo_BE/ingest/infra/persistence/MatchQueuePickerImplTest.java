package com.bestduo_BE.ingest.infra.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.bestduo_BE.ingest.application.port.MatchQueuePicker.MatchQueueItem;
import com.bestduo_BE.common.domain.model.QueueStatus;
import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.persistence.entity.MatchQueue;
import com.bestduo_BE.common.infra.persistence.repository.MatchQueueJpaRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
  @DisplayName("pick — 엔티티를 MatchQueueItem으로 변환한다")
  void pickMapsEntitiesToItems() {
    MatchQueue ready = MatchQueue.newReady("m-1", Tier.EMERALD, 20, null);
    MatchQueue retry = MatchQueue.newReady("m-2", Tier.SILVER, 30, null);
    given(repository.pickReadyOrRetry(2)).willReturn(List.of(ready, retry));

    List<MatchQueueItem> items = picker.pick(2);

    verify(repository).pickReadyOrRetry(2);
    assertThat(items).containsExactly(
        new MatchQueueItem("m-1", Tier.EMERALD, 20),
        new MatchQueueItem("m-2", Tier.SILVER, 30)
    );
  }

  @Test
  @DisplayName("markRunning — 상태를 RUNNING으로 업데이트한다")
  void markRunningUpdatesStatus() {
    MatchQueue mq = MatchQueue.newReady("running", Tier.GOLD, 10, null);
    given(repository.findById("running")).willReturn(Optional.of(mq));

    picker.markRunning("running");

    verify(repository).findById("running");
    assertThat(mq.getStatus()).isEqualTo(QueueStatus.RUNNING);
  }

  @Test
  @DisplayName("markDone — 락을 해제하고 상태를 DONE으로 설정한다")
  void markDoneClearsLock() {
    MatchQueue mq = MatchQueue.newReady("done", Tier.BRONZE, 5, null);
    mq.markRunning();
    given(repository.findById("done")).willReturn(Optional.of(mq));

    picker.markDone("done");

    assertThat(mq.getStatus()).isEqualTo(QueueStatus.DONE);
  }

  @Test
  @DisplayName("markError — 실패 메시지를 기록하고 상태를 ERROR로 설정한다")
  void markErrorRecordsFailureMessage() {
    MatchQueue mq = MatchQueue.newReady("err", Tier.PLATINUM, 1, null);
    mq.markRunning();
    given(repository.findById("err")).willReturn(Optional.of(mq));

    picker.markError("err", "boom");

    assertThat(mq.getStatus()).isEqualTo(QueueStatus.ERROR);
    assertThat(mq.getLastError()).isEqualTo("boom");
  }

  @Test
  @DisplayName("markRunning — 매치를 찾을 수 없으면 예외를 던진다")
  void markRunningThrowsWhenMatchNotFound() {
    given(repository.findById("missing")).willReturn(Optional.empty());

    assertThrows(IllegalStateException.class, () -> picker.markRunning("missing"));
  }
}
