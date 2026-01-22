package com.bestduo_BE.application;

import com.bestduo_BE.application.port.MatchIdsFinder;
import com.bestduo_BE.application.port.SummonerExpandQueue;
import com.bestduo_BE.domain.model.Tier;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SeedExpansionWorker {

  private final SummonerExpandQueue summonerExpandQueue;
  private final MatchIdsFinder matchIdsFinder;

  private final CollectMatchDetailAndSaveRaw collectMatchDetailAndSaveRaw;

  // Phase2B: match에서 participants 추출해 seed 확장
  private final ExpandSeedsFromMatch expandSeedsFromMatch;

  public ExpansionResult execute(int batchSize, int matchesPerPuuid, Tier collectionTier) {
    int puuidPicked = 0;
    int matchIdsFetched = 0;
    int rawCreated = 0;
    int seedsExpanded = 0;

    List<String> puuids = summonerExpandQueue.findReadyPuuds(batchSize);
    puuidPicked = puuids.size();

    for (String puuid : puuids) {
      summonerExpandQueue.markExpandRunning(puuid);

      try {
        List<String> matchIds = matchIdsFinder.findRecentMatchIds(puuid, matchesPerPuuid);
        if (matchIds == null || matchIds.isEmpty()) {
          summonerExpandQueue.markExpandDone(puuid);
          continue;
        }

        matchIdsFetched += matchIds.size();

        for (String matchId : matchIds) {
          if (matchId == null || matchId.isBlank()) continue;

//           1) Phase1: match 저장 + raw 저장
          rawCreated += collectMatchDetailAndSaveRaw.execute(matchId, collectionTier);

//           2) Phase2B: 저장된 match_json에서 participants(10puuid) 뽑아서 seed 추가
          seedsExpanded += expandSeedsFromMatch.execute(matchId);
        }

        summonerExpandQueue.markExpandDone(puuid);

      } catch (Exception e) {
        summonerExpandQueue.markExpandError(puuid);
      }
    }

    return new ExpansionResult(puuidPicked, matchIdsFetched, rawCreated, seedsExpanded);
  }

  public record ExpansionResult(
      int puuidPicked,
      int matchIdsFetched,
      int rawCreated,
      int seedsExpanded
  ) {}
}
