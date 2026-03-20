package com.bestduo_BE.refresh.application.port;

import com.bestduo_BE.common.infra.riot.dto.LeagueEntry;
import java.util.List;

public interface LeagueEntriesRefreshLoader {
  List<LeagueEntry> loadEntriesByPuuid(String puuid);
}
