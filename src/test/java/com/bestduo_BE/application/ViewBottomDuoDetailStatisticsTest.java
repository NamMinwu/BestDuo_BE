package com.bestduo_BE.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bestduo_BE.application.filter.BottomDuoDetailFilterParser;
import com.bestduo_BE.application.port.ChampionMetaClient;
import com.bestduo_BE.domain.model.BottomDuoFilterCriteria;
import com.bestduo_BE.domain.model.BottomDuoMatchupStat;
import com.bestduo_BE.domain.model.BottomDuoStat;
import com.bestduo_BE.domain.model.ChampionMeta;
import com.bestduo_BE.domain.model.SortOption;
import com.bestduo_BE.domain.model.Tier;
import com.bestduo_BE.domain.repository.BottomDuoMatchupStatRepository;
import com.bestduo_BE.domain.repository.BottomDuoStatRepository;
import com.bestduo_BE.domain.service.BottomDuoMatchupRules;
import com.bestduo_BE.presentation.api.dto.BottomDuoDetailStatisticsRequest;
import com.bestduo_BE.presentation.api.dto.BottomDuoDetailStatisticsResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class ViewBottomDuoDetailStatisticsTest {

  private static final BottomDuoMatchupRules MATCHUP_RULES = new BottomDuoMatchupRules();

  @Test
  void 요청부터응답까지_흐름을_연결한다() {
    BottomDuoFilterCriteria criteria =
        new BottomDuoFilterCriteria("ad-1", "sup-1", Tier.EMERALD, SortOption.WIN_RATE);
    StubBottomDuoDetailFilterParser filterParser = new StubBottomDuoDetailFilterParser(criteria);

    RecordingBottomDuoStatRepository statRepository = new RecordingBottomDuoStatRepository();
    statRepository.givenBaseStat(stat("ad-1", "sup-1", Tier.EMERALD, 60, 100));
    statRepository.givenTotalStats(List.of(
        stat("ad-1", "sup-1", Tier.EMERALD, 60, 100),
        stat("ad-2", "sup-2", Tier.EMERALD, 40, 80)
    ));

    RecordingBottomDuoMatchupRepository matchupRepository =
        new RecordingBottomDuoMatchupRepository();
    matchupRepository.givenMatchups(List.of(
        matchup("ad-1", "sup-1", "opp-ad-1", "opp-sup-1", Tier.EMERALD, 10, 50),
        matchup("ad-1", "sup-1", "opp-ad-2", "opp-sup-2", Tier.EMERALD, 25, 100),
        matchup("ad-1", "sup-1", "opp-ad-3", "opp-sup-3", Tier.EMERALD, 5, 25)
    ));

    FakeChampionMetaClient championMetaClient = new FakeChampionMetaClient()
        .withMeta("ad-1", "Ashe", "ashe.png")
        .withMeta("sup-1", "Leona", "leona.png")
        .withMeta("opp-ad-1", "Tristana", "tri.png")
        .withMeta("opp-sup-1", "Nami", "nami.png")
        .withMeta("opp-ad-2", "Jinx", "jinx.png")
        .withMeta("opp-sup-2", "Lulu", "lulu.png")
        .withMeta("opp-ad-3", "Ezreal", "ez.png")
        .withMeta("opp-sup-3", "Yuumi", "yuumi.png");

    ViewBottomDuoDetailStatistics useCase = new ViewBottomDuoDetailStatistics(
        statRepository,
        matchupRepository,
        filterParser,
        MATCHUP_RULES,
        championMetaClient
    );

    BottomDuoDetailStatisticsResponse response =
        useCase.handle(new BottomDuoDetailStatisticsRequest("ad-1", "sup-1", Tier.EMERALD,
            SortOption.WIN_RATE));

    assertEquals("Ashe", response.duo().adName());
    assertEquals("Leona", response.duo().supName());
    assertEquals(0.6, response.duo().winRate(), 1e-6);
    assertEquals(100, response.duo().games());
    assertEquals(100d / 180, response.duo().pickRate(), 1e-6);

    assertEquals(3, response.matchups().size());
    assertEquals(List.of("Tristana", "Jinx", "Ezreal"), response.matchups().stream()
        .map(BottomDuoDetailStatisticsResponse.MatchupStat::opponentAdName)
        .toList());
    assertEquals(3, response.counters().size());
    assertEquals(List.of("Ezreal", "Tristana", "Jinx"), response.counters().stream()
        .map(BottomDuoDetailStatisticsResponse.MatchupStat::opponentAdName)
        .toList());
  }

  @Test
  void 요청이_null이면_기본필터로_동작한다() {
    BottomDuoDetailFilterParser filterParser = new BottomDuoDetailFilterParser();
    RecordingBottomDuoStatRepository statRepository = new RecordingBottomDuoStatRepository();
    statRepository.givenBaseStat(stat("ad", "sup", Tier.ALL_TIERS, 10, 20));
    statRepository.givenTotalStats(List.of());

    RecordingBottomDuoMatchupRepository matchupRepository =
        new RecordingBottomDuoMatchupRepository();
    matchupRepository.givenMatchups(List.of());

    FakeChampionMetaClient championMetaClient = new FakeChampionMetaClient()
        .withMeta("ad", "Ashe", "ashe.png")
        .withMeta("sup", "Leona", "leona.png");

    ViewBottomDuoDetailStatistics useCase = new ViewBottomDuoDetailStatistics(
        statRepository,
        matchupRepository,
        filterParser,
        MATCHUP_RULES,
        championMetaClient
    );

    useCase.handle(null);

    assertEquals(new BottomDuoFilterCriteria(null, null, Tier.ALL_TIERS, SortOption.WIN_RATE),
        matchupRepository.lastRequestedCriteria);
    assertEquals(new BottomDuoFilterCriteria(null, null, Tier.ALL_TIERS, SortOption.WIN_RATE),
        statRepository.lastFindByCriteria);
  }

  @Test
  void 총게임수는_동일티어전체합으로_계산한다() {
    BottomDuoFilterCriteria criteria =
        new BottomDuoFilterCriteria("ad-1", "sup-1", Tier.GOLD, SortOption.WIN_RATE);
    StubBottomDuoDetailFilterParser filterParser = new StubBottomDuoDetailFilterParser(criteria);

    RecordingBottomDuoStatRepository statRepository = new RecordingBottomDuoStatRepository();
    statRepository.givenBaseStat(stat("ad-1", "sup-1", Tier.GOLD, 50, 100));
    statRepository.givenTotalStats(List.of(
        stat("ad-1", "sup-1", Tier.GOLD, 50, 100),
        stat("ad-2", "sup-2", Tier.GOLD, 10, 20)
    ));

    RecordingBottomDuoMatchupRepository matchupRepository =
        new RecordingBottomDuoMatchupRepository();
    matchupRepository.givenMatchups(List.of());

    FakeChampionMetaClient championMetaClient = new FakeChampionMetaClient()
        .withMeta("ad-1", "Ashe", "ashe.png")
        .withMeta("sup-1", "Leona", "leona.png");

    ViewBottomDuoDetailStatistics useCase = new ViewBottomDuoDetailStatistics(
        statRepository,
        matchupRepository,
        filterParser,
        MATCHUP_RULES,
        championMetaClient
    );

    BottomDuoDetailStatisticsResponse response = useCase.handle(
        new BottomDuoDetailStatisticsRequest("ad-1", "sup-1", Tier.GOLD, SortOption.WIN_RATE));

    assertEquals(new BottomDuoFilterCriteria(null, null, Tier.GOLD, SortOption.WIN_RATE),
        statRepository.lastFindByCriteria);
    assertEquals(100, response.duo().games());
    assertEquals(100d / 120, response.duo().pickRate(), 1e-6);
  }

  @Test
  void 카운터는_최저승률순으로_최대N개만_포함한다() {
    BottomDuoFilterCriteria criteria =
        new BottomDuoFilterCriteria("ad-1", "sup-1", Tier.EMERALD, SortOption.WIN_RATE);
    StubBottomDuoDetailFilterParser filterParser = new StubBottomDuoDetailFilterParser(criteria);

    RecordingBottomDuoStatRepository statRepository = new RecordingBottomDuoStatRepository();
    statRepository.givenBaseStat(stat("ad-1", "sup-1", Tier.EMERALD, 10, 20));
    statRepository.givenTotalStats(List.of(stat("ad-1", "sup-1", Tier.EMERALD, 10, 20)));

    RecordingBottomDuoMatchupRepository matchupRepository =
        new RecordingBottomDuoMatchupRepository();
    matchupRepository.givenMatchups(List.of(
        matchup("ad-1", "sup-1", "opp-1", "sup-1", Tier.EMERALD, 10, 50),
        matchup("ad-1", "sup-1", "opp-2", "sup-2", Tier.EMERALD, 20, 60),
        matchup("ad-1", "sup-1", "opp-3", "sup-3", Tier.EMERALD, 5, 30),
        matchup("ad-1", "sup-1", "opp-4", "sup-4", Tier.EMERALD, 15, 80),
        matchup("ad-1", "sup-1", "opp-5", "sup-5", Tier.EMERALD, 25, 100)
    ));

    FakeChampionMetaClient championMetaClient = new FakeChampionMetaClient()
        .withMeta("ad-1", "Ashe", "ashe.png")
        .withMeta("sup-1", "Leona", "leona.png")
        .withMeta("opp-1", "Tristana", "tri.png")
        .withMeta("opp-2", "Jinx", "jinx.png")
        .withMeta("opp-3", "Caitlyn", "cait.png")
        .withMeta("opp-4", "Ezreal", "ez.png")
        .withMeta("opp-5", "Kai'Sa", "kaisa.png")
        .withMeta("sup-2", "Nami", "nami.png")
        .withMeta("sup-3", "Lulu", "lulu.png")
        .withMeta("sup-4", "Yuumi", "yuumi.png")
        .withMeta("sup-5", "Morgana", "morg.png");

    ViewBottomDuoDetailStatistics useCase = new ViewBottomDuoDetailStatistics(
        statRepository,
        matchupRepository,
        filterParser,
        MATCHUP_RULES,
        championMetaClient
    );

    BottomDuoDetailStatisticsResponse response =
        useCase.handle(new BottomDuoDetailStatisticsRequest("ad-1", "sup-1", Tier.EMERALD,
            SortOption.WIN_RATE));

    assertEquals(4, response.counters().size());
    assertEquals(List.of("Caitlyn", "Tristana", "Ezreal", "Jinx"), response.counters().stream()
        .map(BottomDuoDetailStatisticsResponse.MatchupStat::opponentAdName)
        .toList());
  }

  @Test
  void 매치업조회는_정렬옵션을_그대로_전달한다() {
    BottomDuoFilterCriteria criteria =
        new BottomDuoFilterCriteria("ad-1", "sup-1", Tier.PLATINUM, SortOption.PICK_RATE);
    StubBottomDuoDetailFilterParser filterParser = new StubBottomDuoDetailFilterParser(criteria);

    RecordingBottomDuoStatRepository statRepository = new RecordingBottomDuoStatRepository();
    statRepository.givenBaseStat(stat("ad-1", "sup-1", Tier.PLATINUM, 30, 50));
    statRepository.givenTotalStats(List.of(stat("ad-1", "sup-1", Tier.PLATINUM, 30, 50)));

    RecordingBottomDuoMatchupRepository matchupRepository =
        new RecordingBottomDuoMatchupRepository();
    matchupRepository.givenMatchups(List.of());

    FakeChampionMetaClient championMetaClient = new FakeChampionMetaClient()
        .withMeta("ad-1", "Ashe", "ashe.png")
        .withMeta("sup-1", "Leona", "leona.png");

    ViewBottomDuoDetailStatistics useCase = new ViewBottomDuoDetailStatistics(
        statRepository,
        matchupRepository,
        filterParser,
        MATCHUP_RULES,
        championMetaClient
    );

    useCase.handle(new BottomDuoDetailStatisticsRequest("ad-1", "sup-1", Tier.PLATINUM,
        SortOption.PICK_RATE));

    assertEquals(criteria, matchupRepository.lastRequestedCriteria);
  }

  @Test
  void 매치업이_없으면_빈리스트와_0픽률을_반환한다() {
    BottomDuoFilterCriteria criteria =
        new BottomDuoFilterCriteria("ad-1", "sup-1", Tier.SILVER, SortOption.WIN_RATE);
    StubBottomDuoDetailFilterParser filterParser = new StubBottomDuoDetailFilterParser(criteria);

    RecordingBottomDuoStatRepository statRepository = new RecordingBottomDuoStatRepository();
    statRepository.givenBaseStat(stat("ad-1", "sup-1", Tier.SILVER, 0, 0));
    statRepository.givenTotalStats(List.of());

    RecordingBottomDuoMatchupRepository matchupRepository =
        new RecordingBottomDuoMatchupRepository();
    matchupRepository.givenMatchups(List.of());

    FakeChampionMetaClient championMetaClient = new FakeChampionMetaClient()
        .withMeta("ad-1", "Ashe", "ashe.png")
        .withMeta("sup-1", "Leona", "leona.png");

    ViewBottomDuoDetailStatistics useCase = new ViewBottomDuoDetailStatistics(
        statRepository,
        matchupRepository,
        filterParser,
        MATCHUP_RULES,
        championMetaClient
    );

    BottomDuoDetailStatisticsResponse response =
        useCase.handle(new BottomDuoDetailStatisticsRequest("ad-1", "sup-1", Tier.SILVER,
            SortOption.WIN_RATE));

    assertTrue(response.matchups().isEmpty());
    assertTrue(response.counters().isEmpty());
    assertEquals(0, response.duo().games());
    assertEquals(0, response.duo().pickRate());
  }

  private BottomDuoStat stat(String adId, String supId, Tier tier, int wins, int games) {
    return new BottomDuoStat(adId, supId, tier, wins, games);
  }

  private BottomDuoMatchupStat matchup(String myAd, String mySup, String opponentAd,
      String opponentSup, Tier tier, int wins, int games) {
    return new BottomDuoMatchupStat(myAd, mySup, opponentAd, opponentSup, tier, wins, games);
  }

  private static class RecordingBottomDuoStatRepository implements BottomDuoStatRepository {

    private BottomDuoStat baseStat;
    private List<BottomDuoStat> totalStats = List.of();
    private BottomDuoFilterCriteria lastFindByCriteria;

    void givenBaseStat(BottomDuoStat stat) {
      this.baseStat = stat;
    }

    void givenTotalStats(List<BottomDuoStat> stats) {
      this.totalStats = stats;
    }

    @Override
    public List<BottomDuoStat> findBy(BottomDuoFilterCriteria criteria) {
      this.lastFindByCriteria = criteria;
      return totalStats;
    }

    @Override
    public BottomDuoStat findOne(String adId, String supId, Tier tier) {
      return baseStat;
    }

    @Override
    public void save(List<BottomDuoStat> stats) {
      throw new UnsupportedOperationException();
    }
  }

  private static class RecordingBottomDuoMatchupRepository
      implements BottomDuoMatchupStatRepository {

    private List<BottomDuoMatchupStat> matchups = new ArrayList<>();
    private BottomDuoFilterCriteria lastRequestedCriteria;

    void givenMatchups(List<BottomDuoMatchupStat> matchups) {
      this.matchups = matchups;
    }

    @Override
    public List<BottomDuoMatchupStat> findBy(BottomDuoFilterCriteria criteria) {
      this.lastRequestedCriteria = criteria;
      return matchups;
    }
  }

  private static class StubBottomDuoDetailFilterParser extends BottomDuoDetailFilterParser {
    private final BottomDuoFilterCriteria criteria;

    private StubBottomDuoDetailFilterParser(BottomDuoFilterCriteria criteria) {
      this.criteria = criteria;
    }

    @Override
    public BottomDuoFilterCriteria parse(BottomDuoDetailStatisticsRequest request) {
      return criteria;
    }
  }

  private static class FakeChampionMetaClient implements ChampionMetaClient {
    private final Map<String, ChampionMeta> store = new HashMap<>();

    FakeChampionMetaClient withMeta(String id, String name, String image) {
      store.put(id, new ChampionMeta(id, name, image));
      return this;
    }

    @Override
    public ChampionMeta findById(String championId) {
      return Objects.requireNonNull(store.get(championId),
          "Meta not stubbed for id=" + championId);
    }
  }
}
