package com.bestduo_BE.ingest.presentation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bestduo_BE.ingest.application.port.RiotMatchLoader;
import com.bestduo_BE.common.infra.persistence.entity.BottomDuoRawEntity;
import com.bestduo_BE.common.infra.persistence.entity.Match;
import com.bestduo_BE.common.infra.persistence.repository.BottomDuoRawJpaRepository;
import com.bestduo_BE.common.infra.persistence.repository.MatchJpaRepository;
import com.bestduo_BE.common.infra.riot.dto.InfoDto;
import com.bestduo_BE.common.infra.riot.dto.ParticipantDto;
import com.bestduo_BE.common.infra.riot.dto.RiotMatchDto;
import com.bestduo_BE.common.infra.riot.dto.TeamDto;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:collect;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false",
    "external.riot.api-key=dummy"
})
class IngestControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private MatchJpaRepository matchRepository;
  @Autowired private BottomDuoRawJpaRepository bottomDuoRawRepository;
  @Autowired private StubRiotMatchLoader stubRiotMatchLoader;

  @AfterEach
  void cleanUp() {
    bottomDuoRawRepository.deleteAll();
    matchRepository.deleteAll();
  }

  @Test
  @DisplayName("POST /ingest/match — 매치와 BottomDuoRaw를 저장하고 멱등성을 보장한다")
  void collectingMatchPersistsMatchAndBottomDuoRawsIdempotently() throws Exception {
    stubRiotMatchLoader.setResponse(sampleMatch());

    mockMvc.perform(post("/ingest/match/{matchId}", "KR_ABCDE")
            .param("tier", "EMERALD"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("raw_created=2")));

    assertThat(matchRepository.count()).isEqualTo(1);
    Match savedMatch = matchRepository.findAll().get(0);
    assertThat(savedMatch.getPayloadJson()).isNotBlank();

    assertThat(bottomDuoRawRepository.count()).isEqualTo(2);
    List<BottomDuoRawEntity> raws = bottomDuoRawRepository.findAll();
    assertThat(raws).extracting(BottomDuoRawEntity::getTeamId).containsExactlyInAnyOrder(100, 200);

    mockMvc.perform(post("/ingest/match/{matchId}", "KR_ABCDE")
            .param("tier", "EMERALD"))
        .andExpect(status().isOk());

    assertThat(matchRepository.count()).isEqualTo(1);
    assertThat(bottomDuoRawRepository.count()).isEqualTo(2);
  }

  private RiotMatchDto sampleMatch() {
    return new RiotMatchDto(
        null,
        new InfoDto(
            12345L,
            null, null, null, null, null, null, null,
            "15.23.1",
            null, null, 420,
            List.of(
                participant(100, "BOTTOM", 222),
                participant(100, "UTILITY", 412),
                participant(200, "BOTTOM", 51),
                participant(200, "UTILITY", 201)
            ),
            List.of(new TeamDto(100, true), new TeamDto(200, false)),
            null
        )
    );
  }

  private ParticipantDto participant(int teamId, String position, int championId) {
    return new ParticipantDto(
        null, null, null, null,
        null, null,
        championId,
        null,
        null, null,
        null, null, null,
        null, null,
        null, null,
        null,
        teamId,
        position,
        null,
        null,
        null
    );
  }

  @TestConfiguration
  static class StubConfig {
    @Bean
    @Primary
    StubRiotMatchLoader stubRiotMatchLoader() {
      return new StubRiotMatchLoader();
    }
  }

  static class StubRiotMatchLoader implements RiotMatchLoader {
    private RiotMatchDto response;

    void setResponse(RiotMatchDto response) {
      this.response = response;
    }

    @Override
    public RiotMatchDto loadMatch(String matchId) {
      if (response == null) {
        throw new IllegalStateException("No match stub configured");
      }
      return response;
    }
  }
}
