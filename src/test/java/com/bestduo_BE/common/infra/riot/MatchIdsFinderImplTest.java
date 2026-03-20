package com.bestduo_BE.common.infra.riot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.bestduo_BE.common.infra.riot.MatchIdsFinderImpl;
import com.bestduo_BE.common.infra.riot.exception.RiotApiException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
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
}
