package com.bestduo_BE.coverage.application.exception;

public class CoverageBucketAlreadyExistsException extends RuntimeException {

  public CoverageBucketAlreadyExistsException(String patch, String tier) {
    super("Coverage bucket already exists. patch=%s tier=%s".formatted(patch, tier));
  }

  public CoverageBucketAlreadyExistsException(String patch, String tier, Throwable cause) {
    super("Coverage bucket already exists. patch=%s tier=%s".formatted(patch, tier), cause);
  }
}
