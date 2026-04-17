package com.bestduo_BE.common.infra.persistence.projection;

public interface MatchQueueErrorTop {
  String getLastError();
  long getCnt();
}
