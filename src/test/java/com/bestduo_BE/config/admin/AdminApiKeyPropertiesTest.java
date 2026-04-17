package com.bestduo_BE.config.admin;

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

class AdminApiKeyPropertiesTest {

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
  @DisplayName("key가 null이면 검증 위반이 발생한다")
  void validate_nullKey_violates() {
    AdminApiKeyProperties properties = new AdminApiKeyProperties();
    properties.setKey(null);

    Set<ConstraintViolation<AdminApiKeyProperties>> violations = validator.validate(properties);

    assertThat(violations).isNotEmpty();
  }

  @Test
  @DisplayName("key가 빈 문자열이면 검증 위반이 발생한다")
  void validate_blankKey_violates() {
    AdminApiKeyProperties properties = new AdminApiKeyProperties();
    properties.setKey("   ");

    Set<ConstraintViolation<AdminApiKeyProperties>> violations = validator.validate(properties);

    assertThat(violations).isNotEmpty();
  }

  @Test
  @DisplayName("key가 정상 문자열이면 검증이 통과한다")
  void validate_validKey_passes() {
    AdminApiKeyProperties properties = new AdminApiKeyProperties();
    properties.setKey("my-secret-key");

    Set<ConstraintViolation<AdminApiKeyProperties>> violations = validator.validate(properties);

    assertThat(violations).isEmpty();
  }
}
