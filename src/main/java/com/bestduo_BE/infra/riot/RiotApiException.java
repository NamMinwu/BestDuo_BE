package com.bestduo_BE.infra.riot;

public class RiotApiException extends RuntimeException {

  public RiotApiException(String message, Throwable cause) {
    super(message, cause);
  }
}
