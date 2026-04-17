package com.bestduo_BE.aggregate.infra.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.bestduo_BE.aggregate.infra.persistence.entity.BottomDuoStatAggregate;
import com.bestduo_BE.aggregate.infra.persistence.repository.BottomDuoStatAggregateJpaRepository;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:bottom-duo-stat-repo;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create",
    "spring.jpa.show-sql=false",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
    "external.riot.api-key=dummy"
})
class BottomDuoStatAggregateJpaRepositoryTest {

  @Autowired
  private BottomDuoStatAggregateJpaRepository repository;

  @AfterEach
  void cleanUp() {
    repository.deleteAll();
  }

  @Test
  void findLatestPatchVersionUsesNumericOrdering() {
    saveRow("14.9", "Ashe", "Lux");
    saveRow("14.10", "Jinx", "Thresh");
    saveRow("14.11", "Caitlyn", "Morgana");

    String latestPatch = repository.findLatestPatchVersion();

    assertThat(latestPatch).isEqualTo("14.11");
  }

  @Test
  void findLatestPatchVersionIgnoresUnknownPatch() {
    saveRow("UNKNOWN", "Ashe", "Lux");
    saveRow("14.10", "Jinx", "Thresh");

    String latestPatch = repository.findLatestPatchVersion();

    assertThat(latestPatch).isEqualTo("14.10");
  }

  @Test
  void findPreviousPatchVersionUsesNumericOrderingInsteadOfLexicalOrdering() {
    saveRow("14.9", "Ashe", "Lux");
    saveRow("14.10", "Jinx", "Thresh");
    saveRow("14.11", "Caitlyn", "Morgana");

    String previousPatch = repository.findPreviousPatchVersion("14.11");

    assertThat(previousPatch).isEqualTo("14.10");
  }

  @Test
  void findLatestPatchVersionReturnsNullWhenNoParseablePatchExists() {
    saveRow("UNKNOWN", "Ashe", "Lux");

    String latestPatch = repository.findLatestPatchVersion();

    assertThat(latestPatch).isNull();
  }

  @Test
  void findByPatchVersionAndTierReturnsOnlyRequestedScope() {
    saveRow("14.10", "Ashe", "Lux");
    saveRow("14.10", "Jinx", "Thresh", "GOLD");
    saveRow("14.11", "Caitlyn", "Morgana");

    assertThat(repository.findByPatchVersionAndTier("14.10", "EMERALD").stream()
        .map(BottomDuoStatAggregate::getAdcChampionId)
        .toList()).containsExactly("Ashe");
  }

  @Test
  void findByPatchVersionReturnsAllTiersWithinPatch() {
    saveRow("14.10", "Ashe", "Lux");
    saveRow("14.10", "Jinx", "Thresh", "GOLD");
    saveRow("14.11", "Caitlyn", "Morgana");

    assertThat(repository.findByPatchVersion("14.10")).hasSize(2);
  }

  private void saveRow(String patchVersion, String adcChampionId, String supChampionId) {
    saveRow(patchVersion, adcChampionId, supChampionId, "EMERALD");
  }

  private void saveRow(String patchVersion, String adcChampionId, String supChampionId, String tier) {
    OffsetDateTime now = OffsetDateTime.now();
    repository.save(BottomDuoStatAggregate.builder()
        .patchVersion(patchVersion)
        .adcChampionId(adcChampionId)
        .supChampionId(supChampionId)
        .tier(tier)
        .wins(10)
        .games(20)
        .winRate(0.5)
        .pickRate(0.1)
        .adjustedWinRate(0.5)
        .rankScore(0.0)
        .ranking(1)
        .duoTier(1)
        .previousRanking(null)
        .rankDelta(null)
        .rankingStatus(BottomDuoStatAggregate.RankingStatus.RANKED)
        .createdAt(now)
        .updatedAt(now)
        .build());
  }
}
