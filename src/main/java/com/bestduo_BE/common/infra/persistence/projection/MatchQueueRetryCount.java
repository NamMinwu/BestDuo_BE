package com.bestduo_BE.common.infra.persistence.projection;

public interface MatchQueueRetryCount {
  int getRetryCount();
  long getCnt();
}
