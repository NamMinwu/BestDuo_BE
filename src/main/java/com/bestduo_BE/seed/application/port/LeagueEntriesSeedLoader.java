package com.bestduo_BE.seed.application.port;

import com.bestduo_BE.common.infra.riot.dto.LeagueEntry;
import java.util.List;

public interface LeagueEntriesSeedLoader {
  List<LeagueEntry> loadEntries(String queue, String tier, String division, int page);
}
