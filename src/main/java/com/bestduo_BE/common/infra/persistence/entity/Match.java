package com.bestduo_BE.common.infra.persistence.entity;

import com.bestduo_BE.common.domain.model.Tier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

  /**
   * 이 매치를 어느 tier 버킷에서 수집했는지 나타내는 메타.
   * <p>bottom_duo_raw 가 retention 으로 사라져도 match 자체에서 tier 분류를 복구할 수 있게 한다.
   * <p>V4 마이그레이션 이전에 들어온 레거시 row 는 정리되어 NOT NULL 이 보장된다.
   */
  @Enumerated(EnumType.STRING)
  @Column(name = "collection_tier", nullable = false)
  private Tier collectionTier;

  @Column(name = "fetched_at", nullable = false)
  private OffsetDateTime fetchedAt;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
  private String payloadJson;

  public static Match from(
      String matchId,
      Integer queueId,
      Long gameCreation,
      String gameVersion,
      Tier collectionTier,
      String payloadJson) {
    return Match.builder()
        .matchId(matchId)
        .queueId(queueId)
        .gameCreation(gameCreation)
        .gameVersion(gameVersion)
        .collectionTier(collectionTier)
        .fetchedAt(OffsetDateTime.now())
        .payloadJson(payloadJson)
        .build();
  }
}
