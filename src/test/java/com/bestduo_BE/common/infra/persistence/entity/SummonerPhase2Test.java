package com.bestduo_BE.common.infra.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SummonerPhase2Test {

  @Test
  @DisplayName("create() 시 seededAt, matchIdsCollectedAt은 null로 초기화된다")
  void createInitializesSeededAtAndMatchIdsCollectedAtAsNull() {
    Summoner summoner = Summoner.create("p-phase2");

    assertThat(summoner.getSeededAt()).isNull();
    assertThat(summoner.getMatchIdsCollectedAt()).isNull();
  }

  @Test
  @DisplayName("markSeeded()는 seededAt을 갱신한다")
  void markSeededSetsSeededAtAndClearsOldValue() {
    Summoner summoner = Summoner.create("p-seed");
    OffsetDateTime t1 = OffsetDateTime.now().minusMinutes(10);
    OffsetDateTime t2 = OffsetDateTime.now().minusMinutes(1);

    summoner.markSeeded(t1);
    assertThat(summoner.getSeededAt()).isEqualTo(t1);

    summoner.markSeeded(t2);
    assertThat(summoner.getSeededAt()).isEqualTo(t2);
  }

  @Test
  @DisplayName("markMatchIdsCollected()는 matchIdsCollectedAt을 설정한다")
  void markMatchIdsCollectedSetsTimestamp() {
    Summoner summoner = Summoner.create("p-collect");
    OffsetDateTime now = OffsetDateTime.now();

    summoner.markMatchIdsCollected(now);

    assertThat(summoner.getMatchIdsCollectedAt()).isEqualTo(now);
  }

  @Test
  @DisplayName("needsMatchIdsCollection() — matchIdsCollectedAt이 null이면 true")
  void needsMatchIdsCollectionReturnsTrueWhenNeverCollected() {
    Summoner summoner = Summoner.create("p-needs");
    OffsetDateTime seededAt = OffsetDateTime.now().minusMinutes(5);
    summoner.markSeeded(seededAt);

    assertThat(summoner.needsMatchIdsCollection()).isTrue();
  }

  @Test
  @DisplayName("needsMatchIdsCollection() — 수집 시점이 seeded 이전이면 true")
  void needsMatchIdsCollectionReturnsTrueWhenCollectedBeforeSeeded() {
    Summoner summoner = Summoner.create("p-stale");
    OffsetDateTime collectedAt = OffsetDateTime.now().minusHours(2);
    OffsetDateTime seededAt = OffsetDateTime.now().minusHours(1);
    summoner.markMatchIdsCollected(collectedAt);
    summoner.markSeeded(seededAt);

    assertThat(summoner.needsMatchIdsCollection()).isTrue();
  }

  @Test
  @DisplayName("needsMatchIdsCollection() — 수집 시점이 seeded 이후이면 false")
  void needsMatchIdsCollectionReturnsFalseWhenCollectedAfterSeeded() {
    Summoner summoner = Summoner.create("p-fresh");
    OffsetDateTime seededAt = OffsetDateTime.now().minusHours(2);
    OffsetDateTime collectedAt = OffsetDateTime.now().minusHours(1);
    summoner.markSeeded(seededAt);
    summoner.markMatchIdsCollected(collectedAt);

    assertThat(summoner.needsMatchIdsCollection()).isFalse();
  }

  @Test
  @DisplayName("needsMatchIdsCollection() — seededAt이 null이면 false")
  void needsMatchIdsCollectionReturnsFalseWhenNeverSeeded() {
    Summoner summoner = Summoner.create("p-notseeded");

    assertThat(summoner.needsMatchIdsCollection()).isFalse();
  }
}
