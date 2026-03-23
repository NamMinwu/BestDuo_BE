package com.bestduo_BE.ingest.application.port;

public interface MatchQueueErrorTop {
  String getLastError();
  long getCnt();
}
