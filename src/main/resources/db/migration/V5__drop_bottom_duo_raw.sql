-- V5: bottom_duo_raw 테이블 제거.
--
-- 배경:
--   - V4 에서 match.collection_tier 가 NOT NULL 로 자리잡고,
--     이전 PR 에서 cron 집계 경로가 match.payload_json 기반 in-memory
--     누적({@code AggregateBottomDuoFromMatch}) 단일 경로로 통일됐다.
--   - bottom_duo_raw 는 더 이상 어떤 집계에도 기여하지 않으며,
--     ingest 경로도 raw 저장 호출을 제거했다.
--   - 검증 절차(docs/aggregate-from-match-verification.md, per-tier cutoff)
--     를 통해 MATCH 경로가 RAW 경로와 알고리즘 동치임을 확인했다.
--
-- 동작:
--   - 테이블 자체를 drop 한다. 인덱스/제약은 cascade 로 함께 제거된다.
--   - 새 환경(fresh DB)에서는 어차피 ddl-auto 가 bottom_duo_raw 엔티티를
--     만들지 않으므로 IF EXISTS 로 안전하게 통과한다.

DROP TABLE IF EXISTS bottom_duo_raw;
