package com.bestduo_BE.common.infra.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.persistence.entity.MatchQueue;
import com.bestduo_BE.common.infra.persistence.repository.MatchQueueJpaRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MatchQueueEnqueuerTest {

  @Mock
  private MatchQueueJpaRepository repository;

  private MatchQueueEnqueuer enqueuer;

  @BeforeEach
  void setUp() {
    enqueuer = new MatchQueueEnqueuer(repository);
  }

  @Test
  @DisplayName("enqueueAllIdempotent — 신규 매치 ID만 저장한다")
  void saveOnlyNewMatchIds() {
    given(repository.existsById("m-1")).willReturn(false);
    given(repository.existsById("m-2")).willReturn(true);

    enqueuer.enqueueAllIdempotent(List.of("m-1", "m-2"), Tier.EMERALD, "15.23");

    ArgumentCaptor<MatchQueue> captor = ArgumentCaptor.forClass(MatchQueue.class);
    verify(repository).save(captor.capture());
    MatchQueue saved = captor.getValue();
    assertThat(saved.getMatchId()).isEqualTo("m-1");
    assertThat(saved.getCollectionTier()).isEqualTo(Tier.EMERALD);
    assertThat(saved.getPatch()).isEqualTo("15.23");
    verify(repository).existsById("m-1");
    verify(repository).existsById("m-2");
  }

  @Test
  @DisplayName("enqueueAllIdempotent — 모든 ID가 이미 존재하면 저장을 건너뛴다")
  void skipSavingWhenAllIdsExist() {
    given(repository.existsById("m-3")).willReturn(true);

    enqueuer.enqueueAllIdempotent(List.of("m-3"), Tier.GOLD, "15.23");

    verify(repository, never()).save(any());
  }
}
