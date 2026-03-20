package com.bestduo_BE.common.infra.champion.dto;

import lombok.Data;

@Data
public class ChampionDto {
  private String id;
  private String key;
  private String name;
  private ImageDto image;
}
