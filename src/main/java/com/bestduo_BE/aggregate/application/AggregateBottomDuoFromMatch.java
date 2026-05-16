package com.bestduo_BE.aggregate.application;

import com.bestduo_BE.common.domain.model.BottomDuoRaw;
import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.domain.service.BottomDuoExtractor;
import com.bestduo_BE.common.infra.persistence.entity.Match;
import com.bestduo_BE.common.infra.persistence.repository.MatchJpaRepository;
import com.bestduo_BE.common.infra.riot.dto.RiotMatchDto;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class AggregateBottomDuoFromMatch {

  private static final int PAGE_SIZE = 500;
  private static final int UPSERT_BATCH_SIZE = 500;

  private final MatchJpaRepository matchRepository;
  private final BottomDuoExtractor extractor;
  private final ObjectMapper objectMapper;
  private final JdbcTemplate jdbcTemplate;

  public Result execute(String patch, Tier tier, boolean upsert) {
    Map<StatKey, Counter> statAcc = new HashMap<>();
    Map<MatchupKey, Counter> matchupAcc = new HashMap<>();

    int processed = streamAndAccumulate(patch, tier, statAcc, matchupAcc);

    int statUpserted = 0;
    int matchupUpserted = 0;
    if (upsert) {
      statUpserted = bulkUpsertStats(patch, tier, statAcc);
      matchupUpserted = bulkUpsertMatchups(patch, tier, matchupAcc);
    }

    return new Result(
        processed,
        statAcc.size(),
        matchupAcc.size(),
        statUpserted,
        matchupUpserted,
        totalGames(statAcc),
        totalGames(matchupAcc)
    );
  }

  private int bulkUpsertStats(String patch, Tier tier, Map<StatKey, Counter> acc) {
    if (acc.isEmpty()) return 0;
    String sql =
        """
        insert into bottom_duo_stat_agg(
          patch_version, adc_champion_id, sup_champion_id, tier,
          wins, games, win_rate, pick_rate, adjusted_win_rate,
          rank_score, ranking_status, created_at, updated_at
        ) values (?, ?, ?, ?, ?, ?, ?, 0, ?, 0, 'PENDING', now(), now())
        on conflict (patch_version, adc_champion_id, sup_champion_id, tier)
        do update set
          wins = excluded.wins,
          games = excluded.games,
          win_rate = excluded.win_rate,
          pick_rate = excluded.pick_rate,
          adjusted_win_rate = excluded.adjusted_win_rate,
          rank_score = excluded.rank_score,
          ranking_status = excluded.ranking_status,
          updated_at = now()
        """;
    jdbcTemplate.batchUpdate(sql, acc.entrySet(), UPSERT_BATCH_SIZE, (ps, e) -> {
      Counter c = e.getValue();
      double winRate = c.games == 0 ? 0 : ((double) c.wins) / c.games;
      double adjustedWinRate =
          c.games == 0 ? 0 : ((double) c.wins + 50.0) / (c.games + 100);
      ps.setString(1, patch);
      ps.setString(2, Integer.toString(e.getKey().adcId()));
      ps.setString(3, Integer.toString(e.getKey().supId()));
      ps.setString(4, tier.name());
      ps.setInt(5, c.wins);
      ps.setInt(6, c.games);
      ps.setDouble(7, winRate);
      ps.setDouble(8, adjustedWinRate);
    });
    return acc.size();
  }

  private int bulkUpsertMatchups(String patch, Tier tier, Map<MatchupKey, Counter> acc) {
    if (acc.isEmpty()) return 0;
    String sql =
        """
        insert into bottom_duo_matchup_agg(
          patch_version,
          my_adc_champion_id, my_sup_champion_id,
          opp_adc_champion_id, opp_sup_champion_id,
          tier, wins, games, created_at, updated_at
        ) values (?, ?, ?, ?, ?, ?, ?, ?, now(), now())
        on conflict (patch_version, my_adc_champion_id, my_sup_champion_id,
                     opp_adc_champion_id, opp_sup_champion_id, tier)
        do update set
          wins = excluded.wins,
          games = excluded.games,
          updated_at = now()
        """;
    jdbcTemplate.batchUpdate(sql, acc.entrySet(), UPSERT_BATCH_SIZE, (ps, e) -> {
      MatchupKey k = e.getKey();
      Counter c = e.getValue();
      ps.setString(1, patch);
      ps.setString(2, Integer.toString(k.myAdc()));
      ps.setString(3, Integer.toString(k.mySup()));
      ps.setString(4, Integer.toString(k.oppAdc()));
      ps.setString(5, Integer.toString(k.oppSup()));
      ps.setString(6, tier.name());
      ps.setInt(7, c.wins);
      ps.setInt(8, c.games);
    });
    return acc.size();
  }

  private int streamAndAccumulate(
      String patch,
      Tier tier,
      Map<StatKey, Counter> statAcc,
      Map<MatchupKey, Counter> matchupAcc) {
    String cursor = "";
    int total = 0;
    while (true) {
      List<Match> page =
          matchRepository.findPageByTierAndPatch(tier.name(), patch, cursor, PAGE_SIZE);
      if (page.isEmpty()) break;
      for (Match m : page) {
        RiotMatchDto dto = objectMapper.readValue(m.getPayloadJson(), RiotMatchDto.class);
        List<BottomDuoRaw> duos = extractor.extract(m.getMatchId(), dto, tier);
        accumulate(duos, statAcc, matchupAcc);
        cursor = m.getMatchId();
      }
      total += page.size();
    }
    return total;
  }

  private void accumulate(
      List<BottomDuoRaw> duos,
      Map<StatKey, Counter> statAcc,
      Map<MatchupKey, Counter> matchupAcc) {
    for (BottomDuoRaw d : duos) {
      StatKey key = new StatKey(d.adcChampionId(), d.supChampionId());
      statAcc.computeIfAbsent(key, k -> new Counter()).add(d.win());
    }
    if (duos.size() == 2) {
      BottomDuoRaw a = duos.get(0);
      BottomDuoRaw b = duos.get(1);
      MatchupKey aVsB =
          new MatchupKey(a.adcChampionId(), a.supChampionId(), b.adcChampionId(), b.supChampionId());
      MatchupKey bVsA =
          new MatchupKey(b.adcChampionId(), b.supChampionId(), a.adcChampionId(), a.supChampionId());
      matchupAcc.computeIfAbsent(aVsB, k -> new Counter()).add(a.win());
      matchupAcc.computeIfAbsent(bVsA, k -> new Counter()).add(b.win());
    }
  }

  private static long totalGames(Map<?, Counter> acc) {
    long s = 0;
    for (Counter c : acc.values()) s += c.games;
    return s;
  }

  public record Result(
      int matchesProcessed,
      int statKeys,
      int matchupKeys,
      int statUpserted,
      int matchupUpserted,
      long statTotalGames,
      long matchupTotalGames
  ) {}

  private record StatKey(int adcId, int supId) {}

  private record MatchupKey(int myAdc, int mySup, int oppAdc, int oppSup) {}

  private static final class Counter {
    int wins;
    int games;
    void add(boolean win) {
      if (win) wins++;
      games++;
    }
  }
}
