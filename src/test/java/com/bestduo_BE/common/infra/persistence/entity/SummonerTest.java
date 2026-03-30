package com.bestduo_BE.common.infra.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.bestduo_BE.common.domain.model.Tier;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class SummonerTest {

  @Test
  void createInitializesRequiredFields() {
    Summoner summoner = Summoner.create("p-1");

    assertThat(summoner.getPuuid()).isEqualTo("p-1");
    assertThat(summoner.getLastMatchStartTime()).isNull();
    assertThat(summoner.getLastKnownTier()).isNull();
    assertThat(summoner.getTierObservedAt()).isNull();
    assertThat(summoner.getLastSeenPatch()).isNull();
    assertThat(summoner.getCreatedAt()).isNotNull();
    assertThat(summoner.getUpdatedAt()).isNotNull();
  }

  @Test
  void observeTierStoresLatestTierMetadata() {
    Summoner summoner = Summoner.create("p-tier");
    OffsetDateTime observedAt = OffsetDateTime.now().minusMinutes(1);

    summoner.observeTier(Tier.MASTER, observedAt);

    assertThat(summoner.getLastKnownTier()).isEqualTo(Tier.MASTER);
    assertThat(summoner.getTierObservedAt()).isEqualTo(observedAt);
  }

  @Test
  void advanceLastMatchStartTimeUpdatesCursor() {
    Summoner summoner = Summoner.create("p-2");

    summoner.advanceLastMatchStartTime(1234L);

    assertThat(summoner.getLastMatchStartTime()).isEqualTo(1234L);
  }

  @Test
  void advanceLastMatchStartTimeDoesNotMoveCursorBackward() {
    Summoner summoner = Summoner.create("p-3");
    summoner.advanceLastMatchStartTime(2000L);

    summoner.advanceLastMatchStartTime(1500L);

    assertThat(summoner.getLastMatchStartTime()).isEqualTo(2000L);
  }

}
