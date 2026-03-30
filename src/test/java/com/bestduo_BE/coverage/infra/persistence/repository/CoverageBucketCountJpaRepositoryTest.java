package com.bestduo_BE.coverage.infra.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.bestduo_BE.common.domain.model.BottomDuoRaw;
import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.infra.persistence.entity.BottomDuoRawEntity;
import com.bestduo_BE.common.infra.persistence.repository.BottomDuoRawJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:coverage;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create",
    "spring.jpa.show-sql=false",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect"
})
class CoverageBucketCountJpaRepositoryTest {

  @Autowired
  private BottomDuoRawJpaRepository bottomDuoRawRepository;

  @Autowired
  private CoverageBucketCountJpaRepository coverageBucketCountRepository;

  @AfterEach
  void cleanUp() {
    bottomDuoRawRepository.deleteAll();
  }

  @Test
  void countDistinctMatchesCountsOneMatchForTwoTeamRowsAndFiltersByPatchAndTier() {
    bottomDuoRawRepository.save(BottomDuoRawEntity.fromDomain(new BottomDuoRaw("KR_1", 100, 1, 2, true, "15.7", Tier.MASTER)));
    bottomDuoRawRepository.save(BottomDuoRawEntity.fromDomain(new BottomDuoRaw("KR_1", 200, 3, 4, false, "15.7", Tier.MASTER)));
    bottomDuoRawRepository.save(BottomDuoRawEntity.fromDomain(new BottomDuoRaw("KR_2", 100, 5, 6, true, "15.6", Tier.MASTER)));
    bottomDuoRawRepository.save(BottomDuoRawEntity.fromDomain(new BottomDuoRaw("KR_3", 100, 7, 8, true, "15.7", Tier.DIAMOND)));

    long count = coverageBucketCountRepository.countDistinctMatches("15.7", "MASTER");

    assertThat(count).isEqualTo(1L);
  }
}
