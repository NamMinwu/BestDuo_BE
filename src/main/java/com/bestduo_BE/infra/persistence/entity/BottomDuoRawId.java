package com.bestduo_BE.infra.persistence.entity;


import java.io.Serializable;
import java.util.Objects;

public class BottomDuoRawId implements Serializable {

  private String matchId;
  private Integer teamId;

  public BottomDuoRawId() {}

  public BottomDuoRawId(String matchId, Integer teamId) {
    this.matchId = matchId;
    this.teamId = teamId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof BottomDuoRawId that)) return false;
    return Objects.equals(matchId, that.matchId) && Objects.equals(teamId, that.teamId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(matchId, teamId);
  }
}