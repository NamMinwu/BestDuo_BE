package com.bestduo_BE.common.infra.riot;

import static com.bestduo_BE.support.RiotMatchTestData.participant;
import static com.bestduo_BE.support.RiotMatchTestData.riotMatch;
import static com.bestduo_BE.support.RiotMatchTestData.team;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bestduo_BE.common.infra.riot.dto.RiotMatchDto;
import com.bestduo_BE.common.infra.riot.exception.RiotApiException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

class RiotMatchLoaderImplTest {

  @Test
  @DisplayName("loadMatch — 리전 엔드포인트에서 매치 데이터를 가져온다")
  void loadMatch_fetchesMatchFromRegionalEndpoint() {
    RecordingRestTemplate restTemplate = new RecordingRestTemplate(
        riotMatch(
            "15.23.4",
            List.of(
                participant(100, "BOTTOM", 222),
                participant(100, "UTILITY", 412)
            ),
            List.of(team(100, true))
        )
    );
    RiotMatchLoaderImpl loader = new RiotMatchLoaderImpl(restTemplate);

    RiotMatchDto match = loader.loadMatch("KR_123");

    assertSame(restTemplate.response, match);
    assertEquals("/lol/match/v5/matches/{matchId}", restTemplate.lastUrl);
    assertEquals(RiotMatchDto.class, restTemplate.lastResponseType);
    assertArrayEquals(new Object[]{"KR_123"}, restTemplate.lastUriVariables);
  }

  @Test
  @DisplayName("loadMatch — RestClientException을 RiotApiException으로 감싼다")
  void loadMatch_wrapsRestClientExceptionInRiotApiException() {
    RestTemplate throwingTemplate = new RestTemplate() {
      @Override
      public <T> T getForObject(String url, Class<T> responseType, Object... uriVariables)
          throws RestClientException {
        throw new RestClientException("boom");
      }
    };
    RiotMatchLoaderImpl loader = new RiotMatchLoaderImpl(throwingTemplate);

    assertThrows(RiotApiException.class, () -> loader.loadMatch("KR_321"));
  }

  private static class RecordingRestTemplate extends RestTemplate {
    private final RiotMatchDto response;
    private String lastUrl;
    private Class<?> lastResponseType;
    private Object[] lastUriVariables;

    private RecordingRestTemplate(RiotMatchDto response) {
      this.response = response;
    }

    @Override
    public <T> T getForObject(String url, Class<T> responseType, Object... uriVariables)
        throws RestClientException {
      this.lastUrl = url;
      this.lastResponseType = responseType;
      this.lastUriVariables = uriVariables;
      return responseType.cast(response);
    }
  }
}
