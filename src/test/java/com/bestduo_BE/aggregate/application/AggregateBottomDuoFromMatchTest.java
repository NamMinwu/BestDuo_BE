package com.bestduo_BE.aggregate.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bestduo_BE.common.domain.model.BottomDuoRaw;
import com.bestduo_BE.common.domain.model.Tier;
import com.bestduo_BE.common.domain.service.BottomDuoExtractor;
import com.bestduo_BE.common.infra.persistence.entity.Match;
import com.bestduo_BE.common.infra.persistence.repository.MatchJpaRepository;
import com.bestduo_BE.common.infra.riot.dto.RiotMatchDto;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AggregateBottomDuoFromMatchTest {

  private static final String PATCH = "16.9";

  @Mock private MatchJpaRepository matchRepository;
  @Mock private BottomDuoExtractor extractor;
  @Mock private ObjectMapper objectMapper;
  @Mock private JdbcTemplate jdbcTemplate;

  private AggregateBottomDuoFromMatch useCase;

  @BeforeEach
  void setUp() {
    useCase = new AggregateBottomDuoFromMatch(matchRepository, extractor, objectMapper, jdbcTemplate);
  }

  @Test
  @DisplayName("execute — 매치가 없으면 0건 처리하고 upsert 도 호출하지 않는다")
  void execute_emptyResultFromRepo_returnsZeroProcessed() {
    when(matchRepository.findPageByTierAndPatch(eq(Tier.EMERALD.name()), eq(PATCH), anyString(), anyInt()))
        .thenReturn(List.of());

    AggregateBottomDuoFromMatch.Result result = useCase.execute(PATCH, Tier.EMERALD, true);

    assertThat(result.matchesProcessed()).isZero();
    assertThat(result.statKeys()).isZero();
    assertThat(result.matchupKeys()).isZero();
    assertThat(result.statUpserted()).isZero();
    assertThat(result.matchupUpserted()).isZero();
    verifyNoInteractions(jdbcTemplate);
    verifyNoInteractions(extractor);
  }

  @Test
  @DisplayName("execute — 한 매치에서 양 팀 듀오가 나오면 stat 2키, matchup 양방향 2키를 누적한다")
  void execute_singleMatchTwoDuos_accumulatesStatAndMatchup() throws Exception {
    Match m = matchEntity("KR_1");
    when(matchRepository.findPageByTierAndPatch(eq(Tier.EMERALD.name()), eq(PATCH), eq(""), anyInt()))
        .thenReturn(List.of(m));
    when(matchRepository.findPageByTierAndPatch(eq(Tier.EMERALD.name()), eq(PATCH), eq("KR_1"), anyInt()))
        .thenReturn(List.of());
    RiotMatchDto dto = stubDto();
    when(objectMapper.readValue(any(String.class), eq(RiotMatchDto.class))).thenReturn(dto);
    when(extractor.extract("KR_1", dto, Tier.EMERALD)).thenReturn(List.of(
        new BottomDuoRaw("KR_1", 100, 222, 412, true, PATCH, Tier.EMERALD),
        new BottomDuoRaw("KR_1", 200, 51, 201, false, PATCH, Tier.EMERALD)
    ));

    AggregateBottomDuoFromMatch.Result result = useCase.execute(PATCH, Tier.EMERALD, false);

    assertThat(result.matchesProcessed()).isEqualTo(1);
    assertThat(result.statKeys()).isEqualTo(2);          // (222,412), (51,201)
    assertThat(result.matchupKeys()).isEqualTo(2);       // A->B, B->A
    assertThat(result.statTotalGames()).isEqualTo(2L);
    assertThat(result.matchupTotalGames()).isEqualTo(2L);
    verifyNoInteractions(jdbcTemplate);                  // upsert=false
  }

  @Test
  @DisplayName("execute — 같은 듀오 조합이 여러 매치에서 등장하면 wins/games 카운트가 누적된다")
  void execute_multipleMatchesSameDuos_accumulatesCounts() throws Exception {
    Match m1 = matchEntity("KR_1");
    Match m2 = matchEntity("KR_2");
    Match m3 = matchEntity("KR_3");
    when(matchRepository.findPageByTierAndPatch(eq(Tier.EMERALD.name()), eq(PATCH), eq(""), anyInt()))
        .thenReturn(List.of(m1, m2, m3));
    when(matchRepository.findPageByTierAndPatch(eq(Tier.EMERALD.name()), eq(PATCH), eq("KR_3"), anyInt()))
        .thenReturn(List.of());
    RiotMatchDto dto = stubDto();
    when(objectMapper.readValue(any(String.class), eq(RiotMatchDto.class))).thenReturn(dto);
    // 같은 듀오(222,412) vs 같은 듀오(51,201) 가 3매치 — 222팀이 2승 1패
    when(extractor.extract("KR_1", dto, Tier.EMERALD)).thenReturn(List.of(
        new BottomDuoRaw("KR_1", 100, 222, 412, true, PATCH, Tier.EMERALD),
        new BottomDuoRaw("KR_1", 200, 51, 201, false, PATCH, Tier.EMERALD)
    ));
    when(extractor.extract("KR_2", dto, Tier.EMERALD)).thenReturn(List.of(
        new BottomDuoRaw("KR_2", 100, 222, 412, true, PATCH, Tier.EMERALD),
        new BottomDuoRaw("KR_2", 200, 51, 201, false, PATCH, Tier.EMERALD)
    ));
    when(extractor.extract("KR_3", dto, Tier.EMERALD)).thenReturn(List.of(
        new BottomDuoRaw("KR_3", 100, 222, 412, false, PATCH, Tier.EMERALD),
        new BottomDuoRaw("KR_3", 200, 51, 201, true, PATCH, Tier.EMERALD)
    ));

    AggregateBottomDuoFromMatch.Result result = useCase.execute(PATCH, Tier.EMERALD, false);

    assertThat(result.matchesProcessed()).isEqualTo(3);
    assertThat(result.statKeys()).isEqualTo(2);            // 여전히 같은 두 듀오
    assertThat(result.matchupKeys()).isEqualTo(2);         // A->B, B->A
    assertThat(result.statTotalGames()).isEqualTo(6L);     // 각 듀오 3게임씩
    assertThat(result.matchupTotalGames()).isEqualTo(6L);  // 양방향 합산
  }

  @Test
  @DisplayName("execute — upsert=true 면 stat/matchup SQL 을 JdbcTemplate.batchUpdate 로 실행한다")
  void execute_upsertTrue_callsJdbcTemplateBatchUpdate() throws Exception {
    Match m = matchEntity("KR_1");
    when(matchRepository.findPageByTierAndPatch(eq(Tier.EMERALD.name()), eq(PATCH), eq(""), anyInt()))
        .thenReturn(List.of(m));
    when(matchRepository.findPageByTierAndPatch(eq(Tier.EMERALD.name()), eq(PATCH), eq("KR_1"), anyInt()))
        .thenReturn(List.of());
    RiotMatchDto dto = stubDto();
    when(objectMapper.readValue(any(String.class), eq(RiotMatchDto.class))).thenReturn(dto);
    when(extractor.extract("KR_1", dto, Tier.EMERALD)).thenReturn(List.of(
        new BottomDuoRaw("KR_1", 100, 222, 412, true, PATCH, Tier.EMERALD),
        new BottomDuoRaw("KR_1", 200, 51, 201, false, PATCH, Tier.EMERALD)
    ));

    AggregateBottomDuoFromMatch.Result result = useCase.execute(PATCH, Tier.EMERALD, true);

    assertThat(result.statUpserted()).isEqualTo(2);
    assertThat(result.matchupUpserted()).isEqualTo(2);
    // stat + matchup 두 종류의 SQL 이 호출되어야 함
    verify(jdbcTemplate, atLeastOnce()).batchUpdate(
        anyString(), anyCollection(), anyInt(), any(ParameterizedPreparedStatementSetter.class));
  }

  @Test
  @DisplayName("execute — 여러 페이지에 걸쳐 매치가 있을 때 keyset 페이지네이션으로 모두 순회한다")
  void execute_paginatesAcrossMultiplePages() throws Exception {
    Match m1 = matchEntity("KR_1");
    Match m2 = matchEntity("KR_2");
    // 첫 페이지: KR_1
    when(matchRepository.findPageByTierAndPatch(eq(Tier.EMERALD.name()), eq(PATCH), eq(""), anyInt()))
        .thenReturn(List.of(m1));
    // 두번째 페이지: KR_2 (커서가 KR_1 으로 넘어옴)
    when(matchRepository.findPageByTierAndPatch(eq(Tier.EMERALD.name()), eq(PATCH), eq("KR_1"), anyInt()))
        .thenReturn(List.of(m2));
    // 세번째 페이지: 종료
    when(matchRepository.findPageByTierAndPatch(eq(Tier.EMERALD.name()), eq(PATCH), eq("KR_2"), anyInt()))
        .thenReturn(List.of());
    RiotMatchDto dto = stubDto();
    when(objectMapper.readValue(any(String.class), eq(RiotMatchDto.class))).thenReturn(dto);
    when(extractor.extract(any(), any(), any())).thenReturn(List.of());

    AggregateBottomDuoFromMatch.Result result = useCase.execute(PATCH, Tier.EMERALD, false);

    assertThat(result.matchesProcessed()).isEqualTo(2);
  }

  private Match matchEntity(String matchId) {
    return Match.builder()
        .matchId(matchId)
        .payloadJson("{}")
        .build();
  }

  private RiotMatchDto stubDto() {
    return new RiotMatchDto(null, null);
  }
}
