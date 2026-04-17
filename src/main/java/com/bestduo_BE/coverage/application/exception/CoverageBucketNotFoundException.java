package com.bestduo_BE.coverage.application.exception;

public class CoverageBucketNotFoundException extends RuntimeException {

  public CoverageBucketNotFoundException(Long id) {
    super("Coverage bucket not found. id=" + id);
  }
}
