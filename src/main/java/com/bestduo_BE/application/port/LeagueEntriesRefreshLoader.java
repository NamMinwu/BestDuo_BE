package com.bestduo_BE.application.port;

import com.bestduo_BE.infra.riot.dto.LeagueEntry;
import java.util.List;

public interface LeagueEntriesRefreshLoader {
  List<LeagueEntry> loadEntriesByPuuid(String puuid);
}
