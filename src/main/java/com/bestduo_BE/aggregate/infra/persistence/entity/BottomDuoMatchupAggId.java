package com.bestduo_BE.aggregate.infra.persistence.entity;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class BottomDuoMatchupAggId implements Serializable {
  private String patchVersion;
  private String myAdcChampionId;
  private String mySupChampionId;
  private String oppAdcChampionId;
  private String oppSupChampionId;
  private String tier;
}
