package com.bestduo_BE.infra.champion.dto;

import java.util.Map;
import lombok.Data;

@Data
public class ChampionListDto {
  private String type;
  private Map<String, ChampionDto> data;
}
