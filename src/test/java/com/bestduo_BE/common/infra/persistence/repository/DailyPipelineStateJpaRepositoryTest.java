package com.bestduo_BE.common.infra.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.bestduo_BE.common.infra.persistence.entity.DailyPipelineState;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:daily-pipeline;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create",
    "spring.jpa.show-sql=false"
})
class DailyPipelineStateJpaRepositoryTest {

  @Autowired
  private DailyPipelineStateJpaRepository repository;

  @Test
  @DisplayName("findByPipelineDate — 저장된 날짜로 조회하면 해당 상태가 반환된다")
  void findByPipelineDateReturnsSavedState() {
    LocalDate today = LocalDate.of(2026, 4, 14);
    repository.save(DailyPipelineState.create(today));

    Optional<DailyPipelineState> found = repository.findByPipelineDate(today);

    assertThat(found).isPresent();
    assertThat(found.get().getPipelineDate()).isEqualTo(today);
  }

  @Test
  @DisplayName("findByPipelineDate — 없는 날짜로 조회하면 empty가 반환된다")
  void findByPipelineDateReturnsEmptyForUnknownDate() {
    Optional<DailyPipelineState> found = repository.findByPipelineDate(LocalDate.of(2020, 1, 1));

    assertThat(found).isEmpty();
  }

  @Test
  @DisplayName("pipeline_date는 UNIQUE 제약이 있어 같은 날짜로 두 번 저장하면 예외가 발생한다")
  void duplicatePipelineDateThrows() {
    LocalDate today = LocalDate.of(2026, 4, 14);
    repository.saveAndFlush(DailyPipelineState.create(today));

    org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () ->
        repository.saveAndFlush(DailyPipelineState.create(today))
    );
  }
}
