-- V4: match 테이블에 collection_tier(NOT NULL) 컬럼을 추가하고 레거시 row를 정리한다.
--
-- 배경:
--   - 그간 match 테이블은 "어느 tier 버킷에서 수집했는가" 메타를 갖지 않아,
--     bottom_duo_raw 의 사본 정보를 통해서만 tier 분류가 가능했다.
--   - bottom_duo_raw 는 retention 정책으로 top-N patch 만 유지되므로,
--     장기 보관(match.payload_json) 자체에서 tier 를 복구할 수 없는 매치들이 남게 된다.
--   - 이 마이그레이션은 match 가 항상 자기 자신의 tier 메타를 갖게 만들어,
--     향후 bottom_duo_raw 를 점진적으로 제거할 수 있는 발판을 마련한다.
--
-- 데이터 사전 진단 (2026-05-16):
--   - match 전체 ~732k row 중 약 61,855건(≈8.4%)이 match_queue 엔트리 없이 잔존.
--   - 분포 확인 결과 100% 가 retention(top-3) 밖의 옛 patch 또는
--     game_version 이 잘못 파싱된 잡음(예: '.').
--   - 이들은 어차피 현재 retention 정책상 어떤 집계에도 기여하지 않으며,
--     tier 메타도 복구 불가하다. → 본 마이그레이션에서 정리한다.
--
-- 동작 순서:
--   1. match_queue 와 매칭되지 않는 레거시 row 삭제
--      (top-3 patch 중에는 missing_queue=0 이 확인되었으므로 살아있는 데이터에 영향 없음)
--   2. collection_tier 컬럼 추가 (우선 NULL 허용)
--   3. match_queue.collection_tier 로 백필
--   4. NOT NULL 제약 부여
--
-- 환경:
--   - 운영(Railway): 이 스크립트가 1회 실행되어 정리 + 컬럼 + 백필을 수행한다.
--   - 새 환경(fresh DB): match 테이블은 Hibernate ddl-auto 가 만들고,
--     이 스크립트는 빈 테이블 위에서 안전하게 통과한다 (DELETE 0건, UPDATE 0건).
--   - 테스트(H2): spring.flyway.enabled=false 이므로 실행되지 않는다.
--
-- 컬럼 타입은 V3 와 동일하게 Hibernate 6.x PostgreSQL Dialect 기본 매핑을 따른다.
--   @Enumerated(STRING) -> varchar(255)

-- 1) 레거시 매치 정리: match_queue 엔트리가 없는 row 는 tier 복구 불가능하므로 삭제.
DELETE FROM match
 WHERE NOT EXISTS (
     SELECT 1 FROM match_queue mq WHERE mq.match_id = match.match_id
 );

-- 2) collection_tier 컬럼 추가 (백필 동안 NULL 허용).
ALTER TABLE match
    ADD COLUMN IF NOT EXISTS collection_tier varchar(255);

-- 3) match_queue 로부터 백필.
UPDATE match
   SET collection_tier = mq.collection_tier
  FROM match_queue mq
 WHERE match.match_id = mq.match_id
   AND match.collection_tier IS NULL;

-- 4) NOT NULL 제약 부여. 이 시점에 남아있는 row 는 모두 백필되었어야 한다.
ALTER TABLE match
    ALTER COLUMN collection_tier SET NOT NULL;
