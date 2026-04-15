package com.bestduo_BE.common.domain.exception;

public class IllegalStateTransitionException extends RuntimeException {
  public IllegalStateTransitionException(String message) {
    super(message);
  }
}
