package com.bestduo_BE.seed.infra.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.persistence.entity.Summoner;
import com.bestduo_BE.common.infra.persistence.repository.SummonerJpaRepository;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
  @DisplayName("registerIfAbsent — 신규 summoner를 저장하고 true를 반환한다")
  void registerIfAbsent_savesReadySummoner() {
    ArgumentCaptor<Summoner> captor = ArgumentCaptor.forClass(Summoner.class);
    OffsetDateTime now = OffsetDateTime.now();

    boolean isFirst = registry.registerIfAbsent("puuid-1", Tier.DIAMOND, now);

    assertTrue(isFirst);
    then(repository).should().save(captor.capture());
    Summoner saved = captor.getValue();
    assertEquals("puuid-1", saved.getPuuid());
    assertEquals(null, saved.getLastMatchStartTime());
  }

  @Test
  @DisplayName("registerIfAbsent — 신규 summoner에 tier 정보를 저장한다")
  void registerIfAbsent_storesTierForNewSummoner() {
    ArgumentCaptor<Summoner> captor = ArgumentCaptor.forClass(Summoner.class);
    OffsetDateTime now = OffsetDateTime.now();

    boolean isFirst = registry.registerIfAbsent("puuid-1", Tier.DIAMOND, now);

    assertTrue(isFirst);
    then(repository).should().save(captor.capture());
    Summoner saved = captor.getValue();
    assertEquals(Tier.DIAMOND, saved.getLastKnownTier());
    assertNotNull(saved.getTierObservedAt());
  }

  @Test
  @DisplayName("registerIfAbsent — 이미 존재하는 summoner이면 false를 반환한다")
  void registerIfAbsent_returnsFalseWhenAlreadyExists() {
    given(repository.save(any(Summoner.class))).willThrow(new DataIntegrityViolationException("dup"));

    boolean isFirst = registry.registerIfAbsent("puuid-2", Tier.GOLD, OffsetDateTime.now());

    assertFalse(isFirst);
  }
}
