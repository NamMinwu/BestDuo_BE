package com.bestduo_BE.common.infra.champion;


import com.bestduo_BE.common.domain.model.ChampionMeta;
import com.bestduo_BE.common.infra.champion.dto.ChampionDto;
import com.bestduo_BE.common.infra.champion.dto.ChampionListDto;
import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
public class DataDragonChampionDataSource implements ChampionDataSource {

  private final RestTemplate restTemplate = new RestTemplate();

  private static final String VERSION = "15.23.1";
  private static final String JSON_URL =
      "https://ddragon.leagueoflegends.com/cdn/" + VERSION + "/data/ko_KR/champion.json";
  private static final String IMAGE_URL =
      "https://ddragon.leagueoflegends.com/cdn/" + VERSION + "/img/champion/";

  private final Map<String, ChampionMeta> metaStore = new HashMap<>();

  @PostConstruct
  public void loadChampions() {
    ChampionListDto dto = restTemplate.getForObject(JSON_URL, ChampionListDto.class);

    if (dto == null || dto.getData() == null) {
      throw new IllegalStateException("Failed to load Data Dragon champion.json");
    }

    int championCount = dto.getData().size();

    for (ChampionDto champion : dto.getData().values()) {
      String id = champion.getId();
      String key = champion.getKey();
      String name = champion.getName();
      String image = IMAGE_URL + champion.getImage().getFull();

      ChampionMeta meta = new ChampionMeta(id, name, image);

      metaStore.put(key, meta);
    }

    System.out.println("Champion metadata loaded: " + championCount + " champions");
  }

  @Override
  public ChampionMeta getById(String championId) {
    return metaStore.get(championId);
  }
}
