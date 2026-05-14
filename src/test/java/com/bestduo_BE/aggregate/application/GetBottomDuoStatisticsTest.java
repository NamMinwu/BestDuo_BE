package com.bestduo_BE.aggregate.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bestduo_BE.aggregate.infra.persistence.BottomDuoStatFinder;
import com.bestduo_BE.common.application.port.ChampionMetaClient;
import com.bestduo_BE.common.domain.model.Tier;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetBottomDuoStatisticsTest {

  @Mock
  private BottomDuoStatFinder statFinder;

  @Mock
  private ChampionMetaClient championMetaClient;

  @InjectMocks
  private GetBottomDuoStatistics useCase;

  @Test
  @DisplayName("size가 정상 범위면 그대로 maxRows로 전달된다")
  void passesSizeAsMaxRowsWhenWithinRange() {
    stubFinderEmpty("14.10");

    useCase.execute(Tier.GOLD, "14.10", null, null,
        BottomDuoStatFinder.SortKey.PICKRATE_DESC, 50);

    verify(statFinder).findStats(
        eq(Tier.GOLD),
        eq("14.10"),
        eq((String) null),
        eq((String) null),
        eq(BottomDuoStatFinder.SortKey.PICKRATE_DESC),
        anyInt(),
        eq(50)
    );
  }

  @Test
  @DisplayName("size가 0 이하면 1로 보정된다")
  void clampsSizeToMinWhenTooSmall() {
    stubFinderEmpty("14.10");

    useCase.execute(Tier.GOLD, "14.10", null, null,
        BottomDuoStatFinder.SortKey.PICKRATE_DESC, 0);

    var maxRowsCaptor = ArgumentCaptor.forClass(Integer.class);
    verify(statFinder).findStats(
        any(), anyString(), any(), any(), any(), anyInt(), maxRowsCaptor.capture()
    );
    assertThat(maxRowsCaptor.getValue()).isEqualTo(GetBottomDuoStatistics.MIN_SIZE);
  }

  @Test
  @DisplayName("size가 상한을 넘으면 MAX_SIZE(1000)로 보정된다")
  void clampsSizeToMaxWhenTooLarge() {
    stubFinderEmpty("14.10");

    useCase.execute(Tier.GOLD, "14.10", null, null,
        BottomDuoStatFinder.SortKey.PICKRATE_DESC, 99999);

    var maxRowsCaptor = ArgumentCaptor.forClass(Integer.class);
    verify(statFinder).findStats(
        any(), anyString(), any(), any(), any(), anyInt(), maxRowsCaptor.capture()
    );
    assertThat(maxRowsCaptor.getValue()).isEqualTo(GetBottomDuoStatistics.MAX_SIZE);
  }

  private void stubFinderEmpty(String resolvedPatch) {
    when(statFinder.resolvePatchVersion(any())).thenReturn(resolvedPatch);
    when(statFinder.findTierTotalGames(any(), any())).thenReturn(0);
    when(statFinder.findStats(any(), any(), any(), any(), any(), anyInt(), anyInt()))
        .thenReturn(List.of());
  }
}
