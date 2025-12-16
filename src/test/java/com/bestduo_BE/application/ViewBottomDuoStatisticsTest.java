package com.bestduo_BE.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bestduo_BE.application.filter.BottomDuoFilterParser;
import com.bestduo_BE.application.port.ChampionMetaClient;
import com.bestduo_BE.domain.model.BottomDuoFilterCriteria;
import com.bestduo_BE.domain.model.BottomDuoStat;
import com.bestduo_BE.domain.model.ChampionMeta;
import com.bestduo_BE.domain.model.SortOption;
import com.bestduo_BE.domain.model.Tier;
import com.bestduo_BE.domain.repository.BottomDuoStatRepository;
import com.bestduo_BE.infra.repository.InMemoryBottomDuoStatRepository;
import com.bestduo_BE.presentation.api.dto.BottomDuoStatisticsRequest;
import com.bestduo_BE.presentation.api.dto.BottomDuoStatisticsResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class ViewBottomDuoStatisticsTest {

  @Test
  void 챔피언메타와_듀오통계를_합성한다() {
    BottomDuoFilterCriteria criteria = new BottomDuoFilterCriteria(null, null, Tier.EMERALD,
        SortOption.WIN_RATE);

    FakeBottomDuoFilterParser filterParser = new FakeBottomDuoFilterParser(criteria);
    FakeBottomDuoStatRepository repository = new FakeBottomDuoStatRepository();
    FakeChampionMetaClient championMetaClient = new FakeChampionMetaClient()
        .withMeta("ad-1", "Ashe", "ashe.png")
        .withMeta("sup-1", "Leona", "leona.png")
        .withMeta("ad-2", "Jinx", "jinx.png")
        .withMeta("sup-2", "Thresh", "thresh.png");

    repository.givenStats(List.of(
        new BottomDuoStat("ad-1", "sup-1", Tier.EMERALD, 60, 100),
        new BottomDuoStat("ad-2", "sup-2", Tier.EMERALD, 25, 50)
    ));

    ViewBottomDuoStatistics useCase = new ViewBottomDuoStatistics(repository, filterParser,
        championMetaClient);

    BottomDuoStatisticsResponse response = useCase.handle(new BottomDuoStatisticsRequest());

    assertEquals(150, response.totalGames());
    assertEquals(2, response.views().size());

    BottomDuoStatisticsResponse.BottomDuoStatView first = response.views().get(0);
    assertEquals("Ashe", first.adName());
    assertEquals("ashe.png", first.adImage());
    assertEquals("Leona", first.supName());
    assertEquals("leona.png", first.supImage());
    assertEquals(0.6, first.winRate(), 1e-6);
    assertEquals(100, first.games());
    assertEquals(100d / 150, first.pickRate(), 1e-6);

    BottomDuoStatisticsResponse.BottomDuoStatView second = response.views().get(1);
    assertEquals("Jinx", second.adName());
    assertEquals("jinx.png", second.adImage());
    assertEquals("Thresh", second.supName());
    assertEquals("thresh.png", second.supImage());
    assertEquals(0.5, second.winRate(), 1e-6);
    assertEquals(50, second.games());
    assertEquals(50d / 150, second.pickRate(), 1e-6);

    assertEquals(criteria, repository.requestedCriteria);
  }

  @Test
  void 필터없으면_승률기준으로_전체정렬한다() {
    InMemoryBottomDuoStatRepository repository = repositoryWithStats(
        stat("ashe", "leona", Tier.GOLD, 60, 100),
        stat("jinx", "nautilus", Tier.GOLD, 80, 100),
        stat("caitlyn", "lux", Tier.GOLD, 30, 80)
    );

    FakeChampionMetaClient championMetaClient = new FakeChampionMetaClient()
        .withMeta("ashe", "Ashe", "ashe.png")
        .withMeta("leona", "Leona", "leona.png")
        .withMeta("jinx", "Jinx", "jinx.png")
        .withMeta("nautilus", "Nautilus", "nautilus.png")
        .withMeta("caitlyn", "Caitlyn", "caitlyn.png")
        .withMeta("lux", "Lux", "lux.png");

    BottomDuoFilterCriteria criteria =
        new BottomDuoFilterCriteria(null, null, Tier.ALL_TIERS, SortOption.WIN_RATE);

    ViewBottomDuoStatistics useCase = useCase(repository, criteria, championMetaClient);

    BottomDuoStatisticsResponse response = useCase.handle(new BottomDuoStatisticsRequest());

    assertEquals(280, response.totalGames());
    assertEquals(List.of("Jinx", "Ashe", "Caitlyn"), response.views().stream()
        .map(BottomDuoStatisticsResponse.BottomDuoStatView::adName)
        .toList());
    assertEquals(0.8, response.views().get(0).winRate(), 1e-6);
    assertEquals(0.6, response.views().get(1).winRate(), 1e-6);
    assertEquals(100d / 280, response.views().get(0).pickRate(), 1e-6);
  }

  @Test
  void 원딜챔피언으로_필터링한다() {
    InMemoryBottomDuoStatRepository repository = repositoryWithStats(
        stat("ashe", "leona", Tier.GOLD, 60, 100),
        stat("ashe", "morgana", Tier.GOLD, 20, 50),
        stat("jinx", "nautilus", Tier.GOLD, 50, 80)
    );

    FakeChampionMetaClient championMetaClient = new FakeChampionMetaClient()
        .withMeta("ashe", "Ashe", "ashe.png")
        .withMeta("leona", "Leona", "leona.png")
        .withMeta("morgana", "Morgana", "morgana.png")
        .withMeta("jinx", "Jinx", "jinx.png")
        .withMeta("nautilus", "Nautilus", "nautilus.png");

    BottomDuoFilterCriteria criteria =
        new BottomDuoFilterCriteria("ashe", null, Tier.ALL_TIERS, SortOption.WIN_RATE);

    ViewBottomDuoStatistics useCase = useCase(repository, criteria, championMetaClient);

    BottomDuoStatisticsResponse response = useCase.handle(new BottomDuoStatisticsRequest());

    assertEquals(150, response.totalGames());
    assertEquals(List.of("Leona", "Morgana"), response.views().stream()
        .map(BottomDuoStatisticsResponse.BottomDuoStatView::supName)
        .toList());
    assertEquals(List.of("Ashe", "Ashe"), response.views().stream()
        .map(BottomDuoStatisticsResponse.BottomDuoStatView::adName)
        .toList());
  }

  @Test
  void 서폿챔피언으로_필터링한다() {
    InMemoryBottomDuoStatRepository repository = repositoryWithStats(
        stat("ashe", "leona", Tier.GOLD, 60, 100),
        stat("jinx", "leona", Tier.GOLD, 50, 80),
        stat("ashe", "morgana", Tier.GOLD, 35, 50)
    );

    FakeChampionMetaClient championMetaClient = new FakeChampionMetaClient()
        .withMeta("ashe", "Ashe", "ashe.png")
        .withMeta("jinx", "Jinx", "jinx.png")
        .withMeta("leona", "Leona", "leona.png")
        .withMeta("morgana", "Morgana", "morgana.png");

    BottomDuoFilterCriteria criteria =
        new BottomDuoFilterCriteria(null, "leona", Tier.ALL_TIERS, SortOption.WIN_RATE);

    ViewBottomDuoStatistics useCase = useCase(repository, criteria, championMetaClient);

    BottomDuoStatisticsResponse response = useCase.handle(new BottomDuoStatisticsRequest());

    assertEquals(180, response.totalGames());
    assertEquals(List.of("Jinx", "Ashe"), response.views().stream()
        .map(BottomDuoStatisticsResponse.BottomDuoStatView::adName)
        .toList());
    assertEquals(List.of("Leona", "Leona"), response.views().stream()
        .map(BottomDuoStatisticsResponse.BottomDuoStatView::supName)
        .toList());
  }

  @Test
  void 원딜과_서폿조합으로_필터링한다() {
    InMemoryBottomDuoStatRepository repository = repositoryWithStats(
        stat("ashe", "leona", Tier.GOLD, 60, 100),
        stat("ashe", "leona", Tier.PLATINUM, 10, 50),
        stat("jinx", "leona", Tier.GOLD, 50, 80)
    );

    FakeChampionMetaClient championMetaClient = new FakeChampionMetaClient()
        .withMeta("ashe", "Ashe", "ashe.png")
        .withMeta("leona", "Leona", "leona.png")
        .withMeta("jinx", "Jinx", "jinx.png");

    BottomDuoFilterCriteria criteria =
        new BottomDuoFilterCriteria("ashe", "leona", Tier.GOLD, SortOption.WIN_RATE);

    ViewBottomDuoStatistics useCase = useCase(repository, criteria, championMetaClient);

    BottomDuoStatisticsResponse response = useCase.handle(new BottomDuoStatisticsRequest());

    assertEquals(100, response.totalGames());
    assertEquals(1, response.views().size());
    assertEquals("Ashe", response.views().get(0).adName());
    assertEquals("Leona", response.views().get(0).supName());
  }

  @Test
  void 티어로_필터링한다() {
    InMemoryBottomDuoStatRepository repository = repositoryWithStats(
        stat("ashe", "leona", Tier.GOLD, 60, 100),
        stat("ashe", "leona", Tier.PLATINUM, 40, 80),
        stat("jinx", "nautilus", Tier.GOLD, 70, 90)
    );

    FakeChampionMetaClient championMetaClient = new FakeChampionMetaClient()
        .withMeta("ashe", "Ashe", "ashe.png")
        .withMeta("leona", "Leona", "leona.png")
        .withMeta("jinx", "Jinx", "jinx.png")
        .withMeta("nautilus", "Nautilus", "nautilus.png");

    BottomDuoFilterCriteria criteria =
        new BottomDuoFilterCriteria(null, null, Tier.GOLD, SortOption.WIN_RATE);

    ViewBottomDuoStatistics useCase = useCase(repository, criteria, championMetaClient);

    BottomDuoStatisticsResponse response = useCase.handle(new BottomDuoStatisticsRequest());

    assertEquals(190, response.totalGames());
    assertEquals(List.of("Jinx", "Ashe"), response.views().stream()
        .map(BottomDuoStatisticsResponse.BottomDuoStatView::adName)
        .toList());
  }

  @Test
  void 픽률기준으로_내림차순정렬한다() {
    InMemoryBottomDuoStatRepository repository = repositoryWithStats(
        stat("ashe", "leona", Tier.GOLD, 30, 60),
        stat("jinx", "nautilus", Tier.GOLD, 120, 200),
        stat("kaisa", "morgana", Tier.GOLD, 70, 120)
    );

    FakeChampionMetaClient championMetaClient = new FakeChampionMetaClient()
        .withMeta("ashe", "Ashe", "ashe.png")
        .withMeta("leona", "Leona", "leona.png")
        .withMeta("jinx", "Jinx", "jinx.png")
        .withMeta("nautilus", "Nautilus", "nautilus.png")
        .withMeta("kaisa", "Kai'Sa", "kaisa.png")
        .withMeta("morgana", "Morgana", "morgana.png");

    BottomDuoFilterCriteria criteria =
        new BottomDuoFilterCriteria(null, null, Tier.ALL_TIERS, SortOption.PICK_RATE);

    ViewBottomDuoStatistics useCase = useCase(repository, criteria, championMetaClient);

    BottomDuoStatisticsResponse response = useCase.handle(new BottomDuoStatisticsRequest());

    assertEquals(List.of(200, 120, 60), response.views().stream()
        .map(BottomDuoStatisticsResponse.BottomDuoStatView::games)
        .toList());
  }

  @Test
  void 최소게임수미만은_제외한다() {
    InMemoryMinGamesBottomDuoStatRepository repository =
        new InMemoryMinGamesBottomDuoStatRepository(100);
    repository.save(List.of(
        stat("jinx", "nautilus", Tier.GOLD, 100, 120),
        stat("kaisa", "morgana", Tier.GOLD, 90, 150),
        stat("ashe", "leona", Tier.GOLD, 40, 80)
    ));

    FakeChampionMetaClient championMetaClient = new FakeChampionMetaClient()
        .withMeta("jinx", "Jinx", "jinx.png")
        .withMeta("nautilus", "Nautilus", "nautilus.png")
        .withMeta("kaisa", "Kai'Sa", "kaisa.png")
        .withMeta("morgana", "Morgana", "morgana.png")
        .withMeta("ashe", "Ashe", "ashe.png")
        .withMeta("leona", "Leona", "leona.png");

    BottomDuoFilterCriteria criteria =
        new BottomDuoFilterCriteria(null, null, Tier.ALL_TIERS, SortOption.WIN_RATE);

    ViewBottomDuoStatistics useCase = useCase(repository, criteria, championMetaClient);

    BottomDuoStatisticsResponse response = useCase.handle(new BottomDuoStatisticsRequest());

    assertEquals(270, response.totalGames());
    assertEquals(2, response.views().size());
    assertEquals(List.of("Jinx", "Kai'Sa"), response.views().stream()
        .map(BottomDuoStatisticsResponse.BottomDuoStatView::adName)
        .toList());
  }

  private InMemoryBottomDuoStatRepository repositoryWithStats(BottomDuoStat... stats) {
    InMemoryBottomDuoStatRepository repository = new InMemoryBottomDuoStatRepository();
    repository.save(List.of(stats));
    return repository;
  }

  private ViewBottomDuoStatistics useCase(BottomDuoStatRepository repository,
      BottomDuoFilterCriteria criteria, FakeChampionMetaClient championMetaClient) {
    return new ViewBottomDuoStatistics(repository, new FakeBottomDuoFilterParser(criteria),
        championMetaClient);
  }

  private BottomDuoStat stat(String adId, String supId, Tier tier, int wins, int games) {
    return new BottomDuoStat(adId, supId, tier, wins, games);
  }

  private static class InMemoryMinGamesBottomDuoStatRepository
      extends InMemoryBottomDuoStatRepository {

    private final int minGames;

    private InMemoryMinGamesBottomDuoStatRepository(int minGames) {
      this.minGames = minGames;
    }

    @Override
    public List<BottomDuoStat> findBy(BottomDuoFilterCriteria criteria) {
      return super.findBy(criteria).stream()
          .filter(stat -> stat.getGames() >= minGames)
          .toList();
    }
  }

  private static class FakeBottomDuoFilterParser extends BottomDuoFilterParser {
    private final BottomDuoFilterCriteria criteriaToReturn;

    private FakeBottomDuoFilterParser(BottomDuoFilterCriteria criteriaToReturn) {
      this.criteriaToReturn = criteriaToReturn;
    }

    @Override
    public BottomDuoFilterCriteria parse(BottomDuoStatisticsRequest request) {
      return criteriaToReturn;
    }
  }

  private static class FakeBottomDuoStatRepository implements BottomDuoStatRepository {
    private List<BottomDuoStat> stats = new ArrayList<>();
    private BottomDuoFilterCriteria requestedCriteria;

    void givenStats(List<BottomDuoStat> stats) {
      this.stats = stats;
    }

    @Override
    public List<BottomDuoStat> findBy(BottomDuoFilterCriteria criteria) {
      this.requestedCriteria = criteria;
      return stats;
    }

    @Override
    public BottomDuoStat findOne(String adId, String supId, Tier tier) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void save(List<BottomDuoStat> stats) {
      throw new UnsupportedOperationException();
    }
  }

  private static class FakeChampionMetaClient implements ChampionMetaClient {
    private final Map<String, ChampionMeta> metaStore = new HashMap<>();

    FakeChampionMetaClient withMeta(String id, String name, String imageUrl) {
      metaStore.put(id, new ChampionMeta(id, name, imageUrl));
      return this;
    }

    @Override
    public ChampionMeta findById(String championId) {
      ChampionMeta meta = metaStore.get(championId);
      return Objects.requireNonNull(meta, "Meta not stubbed for id=" + championId);
    }
  }
}
