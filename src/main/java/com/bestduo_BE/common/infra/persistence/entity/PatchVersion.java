package com.bestduo_BE.common.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "patch_version")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class PatchVersion {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String patch; // "15.23" (major.minor만)

  @Column(name = "released_at", nullable = false)
  private OffsetDateTime releasedAt; // 최초 감지 시점 (근사값)

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  /** Riot API startTime 파라미터용 epoch seconds */
  public long releasedAtEpochSeconds() {
    return releasedAt.toEpochSecond();
  }

  public static PatchVersion of(String patch, OffsetDateTime releasedAt) {
    return PatchVersion.builder()
        .patch(patch)
        .releasedAt(releasedAt)
        .createdAt(OffsetDateTime.now())
        .build();
  }
}
