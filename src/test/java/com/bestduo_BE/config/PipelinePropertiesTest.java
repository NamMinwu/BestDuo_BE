package com.bestduo_BE.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PipelinePropertiesTest {

  private static ValidatorFactory factory;
  private static Validator validator;

  @BeforeAll
  static void setUp() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void tearDown() {
    factory.close();
  }

  @Test
  @DisplayName("기본값은 검증을 통과한다")
  void validate_defaults_passes() {
    PipelineProperties properties = new PipelineProperties();

    Set<ConstraintViolation<PipelineProperties>> violations = validator.validate(properties);

    assertThat(violations).isEmpty();
  }

  @Test
  @DisplayName("seedDailyBudget이 0이면 검증 위반이 발생한다")
  void validate_zeroSeedBudget_violates() {
    PipelineProperties properties = new PipelineProperties();
    properties.setSeedDailyBudget(0);

    Set<ConstraintViolation<PipelineProperties>> violations = validator.validate(properties);

    assertThat(violations).isNotEmpty();
  }

  @Test
  @DisplayName("seedDailyBudget이 상한을 초과하면 검증 위반이 발생한다")
  void validate_tooLargeSeedBudget_violates() {
    PipelineProperties properties = new PipelineProperties();
    properties.setSeedDailyBudget(50_001);

    Set<ConstraintViolation<PipelineProperties>> violations = validator.validate(properties);

    assertThat(violations).isNotEmpty();
  }

  @Test
  @DisplayName("collectBatchSize가 음수면 검증 위반이 발생한다")
  void validate_negativeBatchSize_violates() {
    PipelineProperties properties = new PipelineProperties();
    properties.setCollectBatchSize(-1);

    Set<ConstraintViolation<PipelineProperties>> violations = validator.validate(properties);

    assertThat(violations).isNotEmpty();
  }

  @Test
  @DisplayName("pollingIntervalMs가 하한 미만이면 검증 위반이 발생한다")
  void validate_tooShortPollingInterval_violates() {
    PipelineProperties properties = new PipelineProperties();
    properties.setPollingIntervalMs(50);

    Set<ConstraintViolation<PipelineProperties>> violations = validator.validate(properties);

    assertThat(violations).isNotEmpty();
  }

  @Test
  @DisplayName("ingest.staleMinutes가 상한을 초과하면 검증 위반이 발생한다 (중첩 @Valid 확인)")
  void validate_tooLargeIngestStaleMinutes_violates() {
    PipelineProperties properties = new PipelineProperties();
    properties.getIngest().setStaleMinutes(2000);

    Set<ConstraintViolation<PipelineProperties>> violations = validator.validate(properties);

    assertThat(violations).isNotEmpty();
  }

  @Test
  @DisplayName("tierMatchCount.apexTiers가 Riot API 상한(100)을 초과하면 검증 위반이 발생한다")
  void validate_tooLargeApexTierMatchCount_violates() {
    PipelineProperties properties = new PipelineProperties();
    properties.getTierMatchCount().setApexTiers(101);

    Set<ConstraintViolation<PipelineProperties>> violations = validator.validate(properties);

    assertThat(violations).isNotEmpty();
  }

  @Test
  @DisplayName("ingest.maxRetry는 0을 허용한다 (재시도 금지 설정)")
  void validate_zeroMaxRetry_passes() {
    PipelineProperties properties = new PipelineProperties();
    properties.getIngest().setMaxRetry(0);

    Set<ConstraintViolation<PipelineProperties>> violations = validator.validate(properties);

    assertThat(violations).isEmpty();
  }
}
