package com.bestduo_BE.common.infra.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SummonerTest {

  @Test
  void markExpandDoneUpdatesLastRunTimestamp() {
    Summoner summoner = Summoner.newReady("p-1");

    assertThat(summoner.getLastExpandRunAt()).isNull();

    summoner.markExpandRunning();

    summoner.markExpandDone();

    assertThat(summoner.getExpandStatus()).isEqualTo("DONE");
    assertThat(summoner.getLastExpandRunAt()).isNotNull();
  }

  @Test
  void markExpandErrorUpdatesLastRunTimestamp() {
    Summoner summoner = Summoner.newReady("p-2");

    summoner.markExpandRunning();

    summoner.markExpandError();

    assertThat(summoner.getExpandStatus()).isEqualTo("ERROR");
    assertThat(summoner.getLastExpandRunAt()).isNotNull();
  }

}
