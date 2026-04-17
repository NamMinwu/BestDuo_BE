package com.bestduo_BE.common.infra.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

/**
 * MatchQueueJpaRepository의 @Modifying 쿼리들이 자체 @Transactional 을 선언하도록 강제한다.
 *
 * <p>현재는 호출자(MatchQueueDispatcher)가 외부 @Transactional 로 감싸고 있어 동작하지만,
 * 외부 트랜잭션 없이 호출되는 경우 jakarta.persistence.TransactionRequiredException 이 발생한다.
 * 레포지토리 메서드에 @Transactional 을 붙여 호출자 실수에도 안전하게 만든다.
 */
class MatchQueueJpaRepositoryTransactionalTest {

  @Test
  @DisplayName("recoverStaleRunning — @Modifying 쿼리는 자체 @Transactional 을 가져야 한다")
  void recoverStaleRunning_mustDeclareTransactional() throws NoSuchMethodException {
    Method method = MatchQueueJpaRepository.class.getMethod("recoverStaleRunning", int.class);

    assertThat(method.getAnnotation(Transactional.class))
        .as("recoverStaleRunning must be annotated with @Transactional — "
            + "@Modifying(flushAutomatically = true) requires an active transaction")
        .isNotNull();
  }

  @Test
  @DisplayName("pickReadyAndLock — @Modifying 쿼리는 자체 @Transactional 을 가져야 한다")
  void pickReadyAndLock_mustDeclareTransactional() throws NoSuchMethodException {
    Method method = MatchQueueJpaRepository.class.getMethod(
        "pickReadyAndLock", int.class, String.class);

    assertThat(method.getAnnotation(Transactional.class))
        .as("pickReadyAndLock must be annotated with @Transactional — "
            + "@Modifying(flushAutomatically = true) requires an active transaction")
        .isNotNull();
  }

  @Test
  @DisplayName("pickRetryableErrorAndLock — @Modifying 쿼리는 자체 @Transactional 을 가져야 한다")
  void pickRetryableErrorAndLock_mustDeclareTransactional() throws NoSuchMethodException {
    Method method = MatchQueueJpaRepository.class.getMethod(
        "pickRetryableErrorAndLock", int.class, int.class, int.class, String.class);

    assertThat(method.getAnnotation(Transactional.class))
        .as("pickRetryableErrorAndLock must be annotated with @Transactional — "
            + "@Modifying(flushAutomatically = true) requires an active transaction")
        .isNotNull();
  }

  @Test
  @DisplayName("unlockToReady — @Modifying 쿼리는 자체 @Transactional 을 가져야 한다")
  void unlockToReady_mustDeclareTransactional() throws NoSuchMethodException {
    Method method = MatchQueueJpaRepository.class.getMethod("unlockToReady", String.class);

    assertThat(method.getAnnotation(Transactional.class))
        .as("unlockToReady must be annotated with @Transactional — "
            + "@Modifying(flushAutomatically = true) requires an active transaction")
        .isNotNull();
  }

  @Test
  @DisplayName("pickReadyWithPriorityAndLock — @Modifying 쿼리는 자체 @Transactional 을 가져야 한다")
  void pickReadyWithPriorityAndLock_mustDeclareTransactional() throws NoSuchMethodException {
    Method method = MatchQueueJpaRepository.class.getMethod(
        "pickReadyWithPriorityAndLock", int.class, String.class, String.class);

    assertThat(method.getAnnotation(Transactional.class))
        .as("pickReadyWithPriorityAndLock must be annotated with @Transactional — "
            + "@Modifying(flushAutomatically = true) requires an active transaction")
        .isNotNull();
  }
}
