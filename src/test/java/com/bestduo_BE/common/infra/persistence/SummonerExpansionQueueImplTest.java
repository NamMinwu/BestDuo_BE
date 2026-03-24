package com.bestduo_BE.common.infra.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.bestduo_BE.common.infra.persistence.repository.SummonerJpaRepository;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SummonerExpansionQueueImplTest {

  @Mock
  private SummonerJpaRepository repository;

  private SummonerExpansionQueueImpl queue;

  @BeforeEach
  void setUp() {
    queue = new SummonerExpansionQueueImpl(repository);
  }

  @Test
  void registerIfAbsentReturnsTrueWhenInsertSucceeds() {
    ArgumentCaptor<OffsetDateTime> nowCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
    given(repository.insertIfAbsent(eq("p-6"), any(OffsetDateTime.class))).willReturn(1);

    boolean registered = queue.registerIfAbsent("p-6");

    assertTrue(registered);
    then(repository).should().insertIfAbsent(eq("p-6"), nowCaptor.capture());
    assertTrue(nowCaptor.getValue().isBefore(OffsetDateTime.now().plusSeconds(1)));
  }

  @Test
  void registerIfAbsentReturnsFalseWhenUniqueConstraintFails() {
    given(repository.insertIfAbsent(eq("existing"), any(OffsetDateTime.class))).willReturn(0);

    assertFalse(queue.registerIfAbsent("existing"));
  }
}
