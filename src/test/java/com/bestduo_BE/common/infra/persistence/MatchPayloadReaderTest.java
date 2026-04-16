package com.bestduo_BE.common.infra.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.bestduo_BE.common.infra.persistence.entity.Match;
import com.bestduo_BE.common.infra.persistence.repository.MatchJpaRepository;
import com.bestduo_BE.common.infra.riot.dto.RiotMatchDto;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class MatchPayloadReaderTest {

  @Mock
  private MatchJpaRepository matchRepository;

  @Mock
  private ObjectMapper objectMapper;

  private MatchPayloadReader reader;

  @BeforeEach
  void setUp() {
    reader = new MatchPayloadReader(matchRepository, objectMapper);
  }

  @Test
  @DisplayName("read — 매치가 존재하면 파싱된 페이로드를 반환한다")
  void read_returnsParsedPayloadWhenMatchExists() throws Exception {
    RiotMatchDto expected = new RiotMatchDto(null, null);
    Match match = Match.builder()
        .matchId("KR_1")
        .queueId(420)
        .gameCreation(1L)
        .gameVersion("15.1")
        .fetchedAt(OffsetDateTime.now())
        .payloadJson("{payload}")
        .build();
    given(matchRepository.findById("KR_1")).willReturn(Optional.of(match));
    given(objectMapper.readValue("{payload}", RiotMatchDto.class)).willReturn(expected);

    RiotMatchDto result = reader.read("KR_1");

    assertSame(expected, result);
    then(matchRepository).should().findById("KR_1");
    then(objectMapper).should().readValue("{payload}", RiotMatchDto.class);
  }

  @Test
  @DisplayName("read — 매치를 찾을 수 없으면 예외를 던진다")
  void read_throwsWhenMatchNotFound() {
    given(matchRepository.findById("KR_MISSING")).willReturn(Optional.empty());

    IllegalStateException exception = assertThrows(IllegalStateException.class,
        () -> reader.read("KR_MISSING"));

    assertEquals("Match not found: KR_MISSING", exception.getMessage());
    then(objectMapper).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("read — JSON 파싱 예외를 IllegalStateException으로 감싼다")
  void read_wrapsJsonParsingException() throws Exception {
    Match match = Match.builder()
        .matchId("KR_2")
        .payloadJson("invalid")
        .fetchedAt(OffsetDateTime.now())
        .build();
    given(matchRepository.findById("KR_2")).willReturn(Optional.of(match));
    RuntimeException parsingException = new RuntimeException("boom");
    given(objectMapper.readValue(eq("invalid"), eq(RiotMatchDto.class))).willThrow(parsingException);

    IllegalStateException exception = assertThrows(IllegalStateException.class,
        () -> reader.read("KR_2"));

    assertEquals("Failed to parse match payload_json for KR_2", exception.getMessage());
    assertSame(parsingException, exception.getCause());
  }
}
