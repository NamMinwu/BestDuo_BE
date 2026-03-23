package com.bestduo_BE.common.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "match")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Match {
  @Id
  @Column(name = "match_id", nullable = false)
  private String matchId;

  @Column(name = "queue_id")
  private Integer queueId;

  @Column(name = "game_creation")
  private Long gameCreation;

  @Column(name = "game_version")
  private String gameVersion;

  @Column(name = "fetched_at", nullable = false)
  private OffsetDateTime fetchedAt;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
  private String payloadJson;

  public static Match from(String matchId, Integer queueId, Long gameCreation, String gameVersion, String payloadJson) {
    return Match.builder()
        .matchId(matchId)
        .queueId(queueId)
        .gameCreation(gameCreation)
        .gameVersion(gameVersion)
        .fetchedAt(OffsetDateTime.now())
        .payloadJson(payloadJson)
        .build();
  }
}
