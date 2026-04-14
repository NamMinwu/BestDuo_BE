package com.bestduo_BE.common.infra.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.persistence.entity.Summoner;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

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
        .lastSeenPatch(null)
        .createdAt(createdAt)
        .updatedAt(updatedAt != null ? updatedAt : OffsetDateTime.now())
        .build();
  }
}
