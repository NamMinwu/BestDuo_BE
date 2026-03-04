package com.bestduo_BE.infra.riot.exception;

public class RiotRateLimitedException extends RuntimeException {
  public RiotRateLimitedException(String message) {
    super(message);
  }
  public RiotRateLimitedException(String message, Throwable cause) {
    super(message, cause);
  }
}
