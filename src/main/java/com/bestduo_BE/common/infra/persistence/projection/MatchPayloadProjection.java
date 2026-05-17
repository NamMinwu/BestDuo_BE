package com.bestduo_BE.common.infra.persistence.projection;

/**
 * Archive 경로 전용 read-only projection.
 *
 * <p>{@code Match} 엔티티를 거치지 않아 Hibernate L1 캐시
 * ({@code StatefulPersistenceContext}) 에 등록되지 않는다. 대용량 payload 를
 * 페이지네이션으로 흘려보낼 때 메모리에 누적되지 않도록 하기 위함.
 */
public interface MatchPayloadProjection {
  String getMatchId();
  String getPayloadJson();
}
