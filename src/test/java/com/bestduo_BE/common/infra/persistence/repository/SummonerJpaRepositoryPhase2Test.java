package com.bestduo_BE.common.infra.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

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
    "spring.datasource.url=jdbc:h2:mem:summoner-phase2;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create",
    "spring.jpa.show-sql=false"
})
class SummonerJpaRepositoryPhase2Test {

  @Autowired
  private SummonerJpaRepository repository;

  @Test
  @DisplayName("findMatchIdsPendingSummoners — seededAt 최신 순, 미수집 summoner 우선 반환")
  void findMatchIdsPendingSummonersReturnsNeverCollectedFirst() {
    OffsetDateTime base = OffsetDateTime.now().minusHours(3);

    // seededAt만 있고 수집 이력 없음 → 대상
    Summoner newer = savedSummoner("newer", base.plusHours(2), null);
    Summoner older = savedSummoner("older", base.plusHours(1), null);
    // 수집 시점이 seededAt 이후 → 제외
    Summoner done = savedSummoner("done", base, base.plusMinutes(30));
    // seededAt 없음 → 제외
    savedSummoner("no-seed", null, null);

    List<Summoner> targets = repository.findMatchIdsPendingSummoners(10);

    assertThat(targets).extracting(Summoner::getPuuid)
        .containsExactly("newer", "older")
        .doesNotContain("done", "no-seed");
  }

  @Test
  @DisplayName("findMatchIdsPendingSummoners — 수집 시점이 seededAt 이전이면 재수집 대상에 포함된다")
  void findMatchIdsPendingSummonersIncludesSummonerWhereCollectedBeforeSeeded() {
    OffsetDateTime seededAt = OffsetDateTime.now().minusHours(1);
    OffsetDateTime collectedAt = OffsetDateTime.now().minusHours(2); // seededAt보다 이전

    Summoner stale = savedSummoner("stale", seededAt, collectedAt);

    List<Summoner> targets = repository.findMatchIdsPendingSummoners(10);

    assertThat(targets).extracting(Summoner::getPuuid).contains("stale");
  }

  @Test
  @DisplayName("findMatchIdsPendingSummoners — limit이 적용된다")
  void findMatchIdsPendingSummonersRespectsLimit() {
    OffsetDateTime base = OffsetDateTime.now().minusHours(5);
    for (int i = 0; i < 5; i++) {
      savedSummoner("p-" + i, base.plusMinutes(i), null);
    }

    List<Summoner> targets = repository.findMatchIdsPendingSummoners(3);

    assertThat(targets).hasSize(3);
  }

  @Test
  @DisplayName("markSeeded — seededAt 컬럼이 DB에 저장된다")
  void markSeededPersistsToDb() {
    repository.save(Summoner.create("p-mark"));
    OffsetDateTime seededAt = OffsetDateTime.now().minusMinutes(5);

    repository.markSeeded("p-mark", seededAt);

    Summoner updated = repository.findById("p-mark").orElseThrow();
    assertThat(updated.getSeededAt()).isNotNull();
  }

  @Test
  @DisplayName("markMatchIdsCollected — matchIdsCollectedAt 컬럼이 DB에 저장된다")
  void markMatchIdsCollectedPersistsToDb() {
    repository.save(Summoner.create("p-collected"));
    OffsetDateTime collectedAt = OffsetDateTime.now().minusMinutes(2);

    repository.markMatchIdsCollected("p-collected", collectedAt);

    Summoner updated = repository.findById("p-collected").orElseThrow();
    assertThat(updated.getMatchIdsCollectedAt()).isNotNull();
  }

  private Summoner savedSummoner(String puuid, OffsetDateTime seededAt, OffsetDateTime matchIdsCollectedAt) {
    OffsetDateTime now = OffsetDateTime.now();
    Summoner s = Summoner.builder()
        .puuid(puuid)
        .lastMatchStartTime(null)
        .lastKnownTier(null)
        .tierObservedAt(null)
        .seededAt(seededAt)
        .matchIdsCollectedAt(matchIdsCollectedAt)
        .createdAt(now)
        .updatedAt(now)
        .build();
    return repository.save(s);
  }
}
