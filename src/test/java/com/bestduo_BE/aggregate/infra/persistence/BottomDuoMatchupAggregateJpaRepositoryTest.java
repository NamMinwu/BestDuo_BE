package com.bestduo_BE.aggregate.infra.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.bestduo_BE.aggregate.infra.persistence.entity.BottomDuoMatchupAggregate;
import com.bestduo_BE.aggregate.infra.persistence.repository.BottomDuoMatchupAggregateJpaRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:bottom-duo-matchup-repo;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create",
    "spring.jpa.show-sql=false",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
    "external.riot.api-key=dummy"
})
class BottomDuoMatchupAggregateJpaRepositoryTest {

  private static final String PATCH = "14.10";
  private static final String TIER = "EMERALD";
  private static final String MY_ADC = "ashe";
  private static final String MY_SUP = "lux";

  @Autowired
  private BottomDuoMatchupAggregateJpaRepository repository;

  @AfterEach
  void cleanUp() {
    repository.deleteAll();
  }

  @Test
  @DisplayName("표본 1판 0승 매치업은 표본 100판 30% 매치업보다 카운터 상위에 오지 않는다")
  void lowSampleZeroWin_doesNotOutrank_largeSampleLowWinRate() {
    saveMatchup("noise", "support1", 1, 0);
    saveMatchup("real", "support2", 100, 30);

    List<BottomDuoMatchupAggregate> rows =
        repository.findCountersByLowestWinRate(PATCH, TIER, MY_ADC, MY_SUP, 10);

    assertThat(rows).hasSize(2);
    assertThat(rows.get(0).getOppAdcChampionId())
        .as("표본 충분한 실제 카운터(100판 30%)가 노이즈(1판 0승)보다 상위에 와야 함")
        .isEqualTo("real");
    assertThat(rows.get(1).getOppAdcChampionId()).isEqualTo("noise");
  }

  @Test
  @DisplayName("표본 200판 35% 매치업이 표본 50판 30% 매치업보다 카운터 상위에 온다 (보정 승률 0.375 < 0.389)")
  void largeSampleSlightlyHigherWinRate_outranks_smallerSampleLowerWinRate() {
    saveMatchup("medium", "support1", 50, 15);
    saveMatchup("large", "support2", 200, 70);

    List<BottomDuoMatchupAggregate> rows =
        repository.findCountersByLowestWinRate(PATCH, TIER, MY_ADC, MY_SUP, 10);

    assertThat(rows).hasSize(2);
    assertThat(rows.get(0).getOppAdcChampionId())
        .as("표본 큰 진짜 카운터(200판 35%)가 보정 승률 더 낮음")
        .isEqualTo("large");
    assertThat(rows.get(1).getOppAdcChampionId()).isEqualTo("medium");
  }

  @Test
  @DisplayName("표본 5판 5승 매치업도 보정 승률 ~0.556로 카운터 상위에 진입하지 못한다")
  void smallSampleAllWins_alsoDoesNotEnterCounterTop() {
    saveMatchup("allwin", "support1", 5, 5);
    saveMatchup("steady", "support2", 100, 40);

    List<BottomDuoMatchupAggregate> rows =
        repository.findCountersByLowestWinRate(PATCH, TIER, MY_ADC, MY_SUP, 10);

    assertThat(rows).hasSize(2);
    assertThat(rows.get(0).getOppAdcChampionId())
        .as("100판 40% (보정 0.429)가 5판 5승 (보정 0.556)보다 카운터 상위")
        .isEqualTo("steady");
    assertThat(rows.get(1).getOppAdcChampionId()).isEqualTo("allwin");
  }

  @Test
  @DisplayName("limit 인자가 결과 개수 상한을 결정한다")
  void limitArgumentCapsResultSize() {
    saveMatchup("a", "s1", 100, 20);
    saveMatchup("b", "s2", 100, 30);
    saveMatchup("c", "s3", 100, 40);

    List<BottomDuoMatchupAggregate> rows =
        repository.findCountersByLowestWinRate(PATCH, TIER, MY_ADC, MY_SUP, 2);

    assertThat(rows).hasSize(2);
  }

  @Test
  @DisplayName("다른 patch/tier/myAdc/mySup 매치업은 제외된다")
  void otherScopeMatchupsAreExcluded() {
    saveMatchup("included", "s1", 100, 10);
    saveMatchupRaw("other-patch", TIER, MY_ADC, MY_SUP, "ex1", "s2", 100, 5);
    saveMatchupRaw(PATCH, "GOLD", MY_ADC, MY_SUP, "ex2", "s3", 100, 5);
    saveMatchupRaw(PATCH, TIER, "jinx", MY_SUP, "ex3", "s4", 100, 5);
    saveMatchupRaw(PATCH, TIER, MY_ADC, "thresh", "ex4", "s5", 100, 5);

    List<BottomDuoMatchupAggregate> rows =
        repository.findCountersByLowestWinRate(PATCH, TIER, MY_ADC, MY_SUP, 10);

    assertThat(rows).extracting(BottomDuoMatchupAggregate::getOppAdcChampionId)
        .containsExactly("included");
  }

  private void saveMatchup(String oppAdc, String oppSup, int games, int wins) {
    saveMatchupRaw(PATCH, TIER, MY_ADC, MY_SUP, oppAdc, oppSup, games, wins);
  }

  private void saveMatchupRaw(
      String patchVersion,
      String tier,
      String myAdc,
      String mySup,
      String oppAdc,
      String oppSup,
      int games,
      int wins
  ) {
    OffsetDateTime now = OffsetDateTime.now();
    BottomDuoMatchupAggregate entity = BottomDuoMatchupAggregate.builder()
        .patchVersion(patchVersion)
        .myAdcChampionId(myAdc)
        .mySupChampionId(mySup)
        .oppAdcChampionId(oppAdc)
        .oppSupChampionId(oppSup)
        .tier(tier)
        .wins(wins)
        .games(games)
        .createdAt(now)
        .updatedAt(now)
        .build();
    repository.save(entity);
  }
}
