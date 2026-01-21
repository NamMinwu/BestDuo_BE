package com.bestduo_BE.application.port;

import java.util.List;

public interface MatchIdsFinder {
  List<String> findRecentMatchIds(String puuid, int count);
}