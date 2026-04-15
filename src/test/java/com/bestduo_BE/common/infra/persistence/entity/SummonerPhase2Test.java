package com.bestduo_BE.common.infra.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SummonerPhase2Test {

  @Test
  @DisplayName("create() 시 leagueEntryFetchedAt, matchIdsCollectedAt은 null로 초기화된다")
  void createInitializesLeagueEntryFetchedAtAndMatchIdsCollectedAtAsNull() {
    Summoner summoner = Summoner.create("p-phase2");

    assertThat(summoner.getLeagueEntryFetchedAt()).isNull();
    assertThat(summoner.getMatchIdsCollectedAt()).isNull();
  }

  @Test
  @DisplayName("markLeagueEntryFetched()는 leagueEntryFetchedAt을 갱신한다")
  void markLeagueEntryFetchedSetsTimestampAndUpdatesOnSubsequentCall() {
    Summoner summoner = Summoner.create("p-seed");
    OffsetDateTime t1 = OffsetDateTime.now().minusMinutes(10);
    OffsetDateTime t2 = OffsetDateTime.now().minusMinutes(1);

    summoner.markLeagueEntryFetched(t1);
    assertThat(summoner.getLeagueEntryFetchedAt()).isEqualTo(t1);

    summoner.markLeagueEntryFetched(t2);
    assertThat(summoner.getLeagueEntryFetchedAt()).isEqualTo(t2);
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
    OffsetDateTime fetchedAt = OffsetDateTime.now().minusMinutes(5);
    summoner.markLeagueEntryFetched(fetchedAt);

    assertThat(summoner.needsMatchIdsCollection()).isTrue();
  }

  @Test
  @DisplayName("needsMatchIdsCollection() — 수집 시점이 leagueEntryFetchedAt 이전이면 true")
  void needsMatchIdsCollectionReturnsTrueWhenCollectedBeforeFetched() {
    Summoner summoner = Summoner.create("p-stale");
    OffsetDateTime collectedAt = OffsetDateTime.now().minusHours(2);
    OffsetDateTime fetchedAt = OffsetDateTime.now().minusHours(1);
    summoner.markMatchIdsCollected(collectedAt);
    summoner.markLeagueEntryFetched(fetchedAt);

    assertThat(summoner.needsMatchIdsCollection()).isTrue();
  }

  @Test
  @DisplayName("needsMatchIdsCollection() — 수집 시점이 leagueEntryFetchedAt 이후이면 false")
  void needsMatchIdsCollectionReturnsFalseWhenCollectedAfterFetched() {
    Summoner summoner = Summoner.create("p-fresh");
    OffsetDateTime fetchedAt = OffsetDateTime.now().minusHours(2);
    OffsetDateTime collectedAt = OffsetDateTime.now().minusHours(1);
    summoner.markLeagueEntryFetched(fetchedAt);
    summoner.markMatchIdsCollected(collectedAt);

    assertThat(summoner.needsMatchIdsCollection()).isFalse();
  }

  @Test
  @DisplayName("needsMatchIdsCollection() — leagueEntryFetchedAt이 null이면 false")
  void needsMatchIdsCollectionReturnsFalseWhenNeverFetched() {
    Summoner summoner = Summoner.create("p-notseeded");

    assertThat(summoner.needsMatchIdsCollection()).isFalse();
  }
}
