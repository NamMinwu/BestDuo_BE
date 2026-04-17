package com.bestduo_BE.common.infra.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.persistence.entity.Summoner;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:summoner-repo;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create",
    "spring.jpa.show-sql=false"
})
class SummonerJpaRepositoryTest {

  @Autowired
  private SummonerJpaRepository repository;

  @Test
  @DisplayName("findRefreshTargets — 요청 tier 우선, 그 다음 tier 미지정 summoner 순으로 반환한다")
  void findRefreshTargetsPrefersRequestedTierThenUnknownTier() {
    Summoner gold = repository.save(summoner("gold", Tier.GOLD, OffsetDateTime.now().minusHours(2), OffsetDateTime.now().minusHours(2)));
    repository.save(summoner("unknown", null, null, OffsetDateTime.now().minusHours(3)));
    repository.save(summoner("silver", Tier.SILVER, OffsetDateTime.now().minusHours(4), OffsetDateTime.now().minusHours(4)));

    List<Summoner> targets = repository.findRefreshTargets(2, Tier.GOLD.name());

    assertThat(targets).extracting(Summoner::getPuuid).containsExactly(gold.getPuuid(), "unknown");
  }

  @Test
  @DisplayName("upsertLeagueEntry — @Modifying 쿼리는 자체 @Transactional 을 가져야 한다")
  void upsertLeagueEntry_mustDeclareTransactional() throws NoSuchMethodException {
    Method method = SummonerJpaRepository.class.getMethod(
        "upsertLeagueEntry", String.class, String.class, OffsetDateTime.class);

    Transactional transactional = method.getAnnotation(Transactional.class);

    assertThat(transactional)
        .as("upsertLeagueEntry must be annotated with @Transactional — "
            + "callers like LeagueEntriesFetcher invoke it outside a managed transaction, "
            + "and @Modifying(flushAutomatically = true) requires an active transaction")
        .isNotNull();
  }

  @Test
  @DisplayName("advanceLastMatchStartTime — @Modifying 쿼리는 자체 @Transactional 을 가져야 한다")
  void advanceLastMatchStartTime_mustDeclareTransactional() throws NoSuchMethodException {
    Method method = SummonerJpaRepository.class.getMethod(
        "advanceLastMatchStartTime", String.class, Long.class);

    assertThat(method.getAnnotation(Transactional.class))
        .as("advanceLastMatchStartTime must be annotated with @Transactional — "
            + "@Modifying(flushAutomatically = true) requires an active transaction, "
            + "and future callers may invoke it outside a managed transaction")
        .isNotNull();
  }

  @Test
  @DisplayName("insertIfAbsent — @Modifying 쿼리는 자체 @Transactional 을 가져야 한다")
  void insertIfAbsent_mustDeclareTransactional() throws NoSuchMethodException {
    Method method = SummonerJpaRepository.class.getMethod(
        "insertIfAbsent", String.class, OffsetDateTime.class);

    assertThat(method.getAnnotation(Transactional.class))
        .as("insertIfAbsent must be annotated with @Transactional — "
            + "@Modifying(flushAutomatically = true) requires an active transaction, "
            + "and future callers may invoke it outside a managed transaction")
        .isNotNull();
  }

  @Test
  @DisplayName("updateTierMetadata — @Modifying 쿼리는 자체 @Transactional 을 가져야 한다")
  void updateTierMetadata_mustDeclareTransactional() throws NoSuchMethodException {
    Method method = SummonerJpaRepository.class.getMethod(
        "updateTierMetadata", String.class, String.class, OffsetDateTime.class);

    assertThat(method.getAnnotation(Transactional.class))
        .as("updateTierMetadata must be annotated with @Transactional — "
            + "@Modifying(flushAutomatically = true) requires an active transaction, "
            + "and future callers may invoke it outside a managed transaction")
        .isNotNull();
  }

  @Test
  @DisplayName("updateTierMetadata — 관측된 tier 정보를 DB에 저장한다")
  void updateTierMetadataStoresObservedTier() {
    repository.save(Summoner.create("p-1"));
    OffsetDateTime observedAt = OffsetDateTime.now().minusMinutes(5);

    repository.updateTierMetadata("p-1", Tier.MASTER.name(), observedAt);

    Summoner updated = repository.findById("p-1").orElseThrow();
    assertThat(updated.getLastKnownTier()).isEqualTo(Tier.MASTER);
    assertThat(updated.getTierObservedAt()).isEqualTo(observedAt);
  }

  private Summoner summoner(String puuid, Tier tier, OffsetDateTime observedAt, OffsetDateTime updatedAt) {
    OffsetDateTime createdAt = updatedAt != null ? updatedAt.minusHours(1) : OffsetDateTime.now().minusHours(1);
    return Summoner.builder()
        .puuid(puuid)
        .lastMatchStartTime(null)
        .lastKnownTier(tier)
        .tierObservedAt(observedAt)
        .createdAt(createdAt)
        .updatedAt(updatedAt != null ? updatedAt : OffsetDateTime.now())
        .build();
  }
}
