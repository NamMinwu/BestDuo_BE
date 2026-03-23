package com.bestduo_BE.seed.infra.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.bestduo_BE.common.infra.persistence.entity.Summoner;
import com.bestduo_BE.common.infra.persistence.repository.SummonerJpaRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class SummonerSeedRegistryImplTest {

  @Mock
  private SummonerJpaRepository repository;

  private SummonerSeedRegistryImpl registry;

  @BeforeEach
  void setUp() {
    registry = new SummonerSeedRegistryImpl(repository);
  }

  @Test
  void registerIfAbsent_savesReadySummoner() {
    ArgumentCaptor<Summoner> captor = ArgumentCaptor.forClass(Summoner.class);

    boolean isFirst = registry.registerIfAbsent("puuid-1");

    assertTrue(isFirst);
    then(repository).should().save(captor.capture());
    Summoner saved = captor.getValue();
    assertEquals("puuid-1", saved.getPuuid());
    assertEquals("READY", saved.getSeedStatus());
    assertNull(saved.getLastSeedRunAt());
  }

  @Test
  void registerIfAbsent_returnsFalseWhenAlreadyExists() {
    given(repository.save(any(Summoner.class))).willThrow(new DataIntegrityViolationException("dup"));

    boolean isFirst = registry.registerIfAbsent("puuid-2");

    assertFalse(isFirst);
  }

  @Test
  void markSeedRunning_updatesStatusAndTimestampsWhenSummonerExists() {
    Summoner entity = existingSummoner("puuid-3", "READY");
    given(repository.findById("puuid-3")).willReturn(Optional.of(entity));

    registry.markSeedRunning("puuid-3");

    then(repository).should().findById(eq("puuid-3"));
    assertEquals("RUNNING", entity.getSeedStatus());
    assertNotNull(entity.getLastSeedRunAt());
  }

  @Test
  void markSeedDone_updatesStatusWhenSummonerExists() {
    Summoner entity = existingSummoner("puuid-4", "RUNNING");
    given(repository.findById("puuid-4")).willReturn(Optional.of(entity));

    registry.markSeedDone("puuid-4");

    assertEquals("DONE", entity.getSeedStatus());
  }

  @Test
  void markSeedError_updatesStatusWhenSummonerExists() {
    Summoner entity = existingSummoner("puuid-5", "RUNNING");
    given(repository.findById("puuid-5")).willReturn(Optional.of(entity));

    registry.markSeedError("puuid-5");

    assertEquals("ERROR", entity.getSeedStatus());
  }

  private Summoner existingSummoner(String puuid, String status) {
    OffsetDateTime now = OffsetDateTime.now().minusDays(1);
    return Summoner.builder()
        .puuid(puuid)
        .seedStatus(status)
        .lastSeedRunAt(null)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }
}
