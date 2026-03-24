package com.bestduo_BE.seed.infra.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.bestduo_BE.common.infra.persistence.entity.Summoner;
import com.bestduo_BE.common.infra.persistence.repository.SummonerJpaRepository;
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
    assertEquals(null, saved.getLastMatchStartTime());
  }

  @Test
  void registerIfAbsent_returnsFalseWhenAlreadyExists() {
    given(repository.save(any(Summoner.class))).willThrow(new DataIntegrityViolationException("dup"));

    boolean isFirst = registry.registerIfAbsent("puuid-2");

    assertFalse(isFirst);
  }
}
