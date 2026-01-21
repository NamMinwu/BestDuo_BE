package com.bestduo_BE.application.port;

import com.bestduo_BE.infra.riot.dto.LeagueEntry;
import java.util.List;

public interface LeagueEntriesLoader {
  List<LeagueEntry> loadEntries(String queue, String tier, String division, int page);
}
