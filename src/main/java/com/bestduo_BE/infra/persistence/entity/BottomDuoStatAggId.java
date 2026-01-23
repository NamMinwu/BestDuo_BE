package com.bestduo_BE.infra.persistence.entity;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class BottomDuoStatAggId implements Serializable {
  private String adcChampionId;
  private String supChampionId;
  private String tier;
}
