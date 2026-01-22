package com.bestduo_BE.infra.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.bestduo_BE.infra.persistence.entity.Summoner;
import com.bestduo_BE.infra.persistence.repository.SummonerJpaRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class SummonerExpandQueueImplTest {

  @Mock
  private SummonerJpaRepository repository;

  private SummonerExpandQueueImpl queue;

  @BeforeEach
  void setUp() {
    queue = new SummonerExpandQueueImpl(repository);
  }

  @Test
  void findReadyPuudsPullsOldestReadySummoners() {
    given(repository.findByExpandStatusOrderByUpdatedAtAsc(eq("READY"), any(Pageable.class)))
        .willReturn(List.of(Summoner.newReady("p-1"), Summoner.newReady("p-2")));

    List<String> result = queue.findReadyPuuds(2);

    assertEquals(List.of("p-1", "p-2"), result);
    ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
    then(repository).should().findByExpandStatusOrderByUpdatedAtAsc(eq("READY"), captor.capture());
    assertEquals(0, captor.getValue().getPageNumber());
    assertEquals(2, captor.getValue().getPageSize());
  }

  @Test
  void markExpandRunningUpdatesEntityWhenPresent() {
    Summoner summoner = Summoner.newReady("p-3");
    given(repository.findById("p-3")).willReturn(Optional.of(summoner));

    queue.markExpandRunning("p-3");

    assertEquals("RUNNING", summoner.getExpandStatus());
    then(repository).should().findById("p-3");
  }

  @Test
  void markExpandDoneUpdatesEntityWhenPresent() {
    Summoner summoner = Summoner.newReady("p-4");
    given(repository.findById("p-4")).willReturn(Optional.of(summoner));

    queue.markExpandDone("p-4");

    assertEquals("DONE", summoner.getExpandStatus());
  }

  @Test
  void markExpandErrorUpdatesEntityWhenPresent() {
    Summoner summoner = Summoner.newReady("p-5");
    given(repository.findById("p-5")).willReturn(Optional.of(summoner));

    queue.markExpandError("p-5");

    assertEquals("ERROR", summoner.getExpandStatus());
  }

  @Test
  void registerIfAbsentReturnsTrueWhenInsertSucceeds() {
    ArgumentCaptor<OffsetDateTime> nowCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
    given(repository.insertReadyIfAbsent(eq("p-6"), any(OffsetDateTime.class))).willReturn(1);

    boolean registered = queue.registerIfAbsent("p-6");

    assertTrue(registered);
    then(repository).should().insertReadyIfAbsent(eq("p-6"), nowCaptor.capture());
    assertTrue(nowCaptor.getValue().isBefore(OffsetDateTime.now().plusSeconds(1)));
  }

  @Test
  void registerIfAbsentReturnsFalseWhenUniqueConstraintFails() {
    given(repository.insertReadyIfAbsent(eq("existing"), any(OffsetDateTime.class))).willReturn(0);

    assertFalse(queue.registerIfAbsent("existing"));
  }
}
