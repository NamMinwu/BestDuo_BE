package com.bestduo_BE.ingest.application.port;

public interface MatchQueueRetryCount {
  int getRetryCount();
  long getCnt();
}
