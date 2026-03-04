package com.bestduo_BE.infra.riot.exception;

public class RiotApiException extends RuntimeException {

  public RiotApiException(String message, Throwable cause) {
    super(message, cause);
  }
}
