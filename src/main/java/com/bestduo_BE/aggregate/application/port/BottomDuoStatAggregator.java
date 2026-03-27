package com.bestduo_BE.aggregate.application.port;

public interface BottomDuoStatAggregator {
  int aggregate(String patchVersion, String tier);
}
