package com.bestduo_BE.common.infra.riot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.bestduo_BE.common.infra.riot.exception.RiotApiException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class MatchIdsFinderImplTest {

  @Mock
  private RestTemplate regionalRestTemplate;

  private MatchIdsFinderImpl finder;

  @BeforeEach
  void setUp() {
    finder = new MatchIdsFinderImpl(regionalRestTemplate);
  }

  @Test
  @DisplayName("findRecentMatchIds — 리전 엔드포인트에서 매치 ID 목록을 반환한다")
  void findRecentMatchIds_returnsIdsFromRegionalEndpoint() {
    given(regionalRestTemplate.getForObject(
        "/lol/match/v5/matches/by-puuid/{puuid}/ids?count={count}",
        String[].class,
        "puuid-123",
        20
    )).willReturn(new String[]{"KR_1", "KR_2"});

    List<String> result = finder.findRecentMatchIds("puuid-123", 20);

    assertEquals(List.of("KR_1", "KR_2"), result);
    then(regionalRestTemplate).should().getForObject(
        eq("/lol/match/v5/matches/by-puuid/{puuid}/ids?count={count}"),
        eq(String[].class),
        eq("puuid-123"),
        eq(20)
    );
  }

  @Test
  @DisplayName("findRecentMatchIds — API가 null을 반환하면 빈 리스트를 반환한다")
  void findRecentMatchIds_returnsEmptyListWhenApiReturnsNull() {
    given(regionalRestTemplate.getForObject(
        "/lol/match/v5/matches/by-puuid/{puuid}/ids?count={count}",
        String[].class,
        "puuid-456",
        10
    )).willReturn(null);

    List<String> result = finder.findRecentMatchIds("puuid-456", 10);

    assertTrue(result.isEmpty());
  }

  @Test
  @DisplayName("findRecentMatchIds — REST 예외를 RiotApiException으로 감싼다")
  void findRecentMatchIds_wrapsRestExceptionsInRiotApiException() {
    given(regionalRestTemplate.getForObject(
        "/lol/match/v5/matches/by-puuid/{puuid}/ids?count={count}",
        String[].class,
        "puuid-789",
        5
    )).willThrow(new RestClientException("boom"));

    assertThrows(RiotApiException.class,
        () -> finder.findRecentMatchIds("puuid-789", 5));
  }

  @Test
  @DisplayName("findMatchIdsSince — 특정 시각 이후의 매치 ID 목록을 반환한다")
  void findMatchIdsSince_returnsIdsFromRegionalEndpoint() {
    given(regionalRestTemplate.getForObject(
        "/lol/match/v5/matches/by-puuid/{puuid}/ids?startTime={startTime}&count={count}",
        String[].class,
        "puuid-abc",
        1700000000L,
        30
    )).willReturn(new String[]{"KR_10", "KR_11"});

    List<String> result = finder.findMatchIdsSince("puuid-abc", 1700000000L, 30);

    assertEquals(List.of("KR_10", "KR_11"), result);
    then(regionalRestTemplate).should().getForObject(
        eq("/lol/match/v5/matches/by-puuid/{puuid}/ids?startTime={startTime}&count={count}"),
        eq(String[].class),
        eq("puuid-abc"),
        eq(1700000000L),
        eq(30)
    );
  }

  @Test
  @DisplayName("findMatchIdsSince — REST 예외를 RiotApiException으로 감싼다")
  void findMatchIdsSince_wrapsRestExceptionsInRiotApiException() {
    given(regionalRestTemplate.getForObject(
        "/lol/match/v5/matches/by-puuid/{puuid}/ids?startTime={startTime}&count={count}",
        String[].class,
        "puuid-def",
        1600000000L,
        15
    )).willThrow(new RestClientException("oops"));

    assertThrows(RiotApiException.class,
        () -> finder.findMatchIdsSince("puuid-def", 1600000000L, 15));
  }

  @Test
  @DisplayName("findMatchIdsBetween — startTime과 endTime을 포함한 매치 ID 목록을 반환한다")
  void findMatchIdsBetween_returnsIdsWithBothTimeParams() {
    given(regionalRestTemplate.getForObject(
        "/lol/match/v5/matches/by-puuid/{puuid}/ids?startTime={startTime}&endTime={endTime}&count={count}",
        String[].class,
        "puuid-xyz",
        1700000000L,
        1700259200L,
        10
    )).willReturn(new String[]{"KR_20", "KR_21"});

    List<String> result = finder.findMatchIdsBetween("puuid-xyz", 1700000000L, 1700259200L, 10);

    assertEquals(List.of("KR_20", "KR_21"), result);
    then(regionalRestTemplate).should().getForObject(
        eq("/lol/match/v5/matches/by-puuid/{puuid}/ids?startTime={startTime}&endTime={endTime}&count={count}"),
        eq(String[].class),
        eq("puuid-xyz"),
        eq(1700000000L),
        eq(1700259200L),
        eq(10)
    );
  }

  @Test
  @DisplayName("findMatchIdsBetween — API가 null을 반환하면 빈 리스트를 반환한다")
  void findMatchIdsBetween_returnsEmptyListWhenApiReturnsNull() {
    given(regionalRestTemplate.getForObject(
        "/lol/match/v5/matches/by-puuid/{puuid}/ids?startTime={startTime}&endTime={endTime}&count={count}",
        String[].class,
        "puuid-xyz",
        1700000000L,
        1700259200L,
        10
    )).willReturn(null);

    List<String> result = finder.findMatchIdsBetween("puuid-xyz", 1700000000L, 1700259200L, 10);

    assertTrue(result.isEmpty());
  }

  @Test
  @DisplayName("findMatchIdsBetween — REST 예외를 RiotApiException으로 감싼다")
  void findMatchIdsBetween_wrapsRestExceptionsInRiotApiException() {
    given(regionalRestTemplate.getForObject(
        "/lol/match/v5/matches/by-puuid/{puuid}/ids?startTime={startTime}&endTime={endTime}&count={count}",
        String[].class,
        "puuid-xyz",
        1700000000L,
        1700259200L,
        10
    )).willThrow(new RestClientException("timeout"));

    assertThrows(RiotApiException.class,
        () -> finder.findMatchIdsBetween("puuid-xyz", 1700000000L, 1700259200L, 10));
  }
}
