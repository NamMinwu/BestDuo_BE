package com.bestduo_BE.infra.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.bestduo_BE.infra.persistence.entity.Match;
import com.bestduo_BE.infra.persistence.repository.MatchJpaRepository;
import com.bestduo_BE.infra.riot.dto.InfoDto;
import com.bestduo_BE.infra.riot.dto.RiotMatchDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class MatchSaverImplTest {

  @Mock
  private MatchJpaRepository matchRepository;

  @Mock
  private ObjectMapper objectMapper;

  private MatchSaverImpl saver;

  @BeforeEach
  void setUp() {
    saver = new MatchSaverImpl(matchRepository, objectMapper);
  }

  @Test
  void save_persistsMatchEntityBuiltFromInfoDto() {
    RiotMatchDto dto = new RiotMatchDto(
        null,
        new InfoDto(
            12345L, null, null, null, null, null, null, null,
            "15.1",
            null, null, 420,
            null,
            null,
            null
        )
    );
    given(objectMapper.writeValueAsString(dto)).willReturn("{payload}");

    saver.save("KR_TEST", dto);

    ArgumentCaptor<Match> captor = ArgumentCaptor.forClass(Match.class);
    then(matchRepository).should().save(captor.capture());
    Match saved = captor.getValue();
    assertEquals("KR_TEST", saved.getMatchId());
    assertEquals(420, saved.getQueueId());
    assertEquals(dto.info().gameCreation(), saved.getGameCreation());
    assertEquals("15.1", saved.getGameVersion());
    assertEquals("{payload}", saved.getPayloadJson());
    then(objectMapper).should().writeValueAsString(dto);
  }
}
