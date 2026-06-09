-- V7: match_queue 테이블 제거 (ADR-008 — inline 파이프라인 전환 완료, Phase C)
--
-- 배경:
--   - Stage 2→3 가 inline 융합되어 enqueue/dequeue 경로가 제거됨 (Phase B).
--   - dedup 은 match.existsById, 재시작 영속은 summoner.match_ids_collected_at,
--     관측은 emit 메트릭(pipeline.ingest.*)이 대체한다.
--   - match_queue 를 참조하는 코드(엔티티/repo/dispatcher/enqueuer/runner/admin/gauge)는
--     모두 제거됨. DONE 행이 무한 증가해 디스크(NFR-6)를 잠식하므로 테이블째 회수한다.
--
--   - 되돌리기 불가: 큐의 과거 status/이력 행이 영구 삭제된다(더 이상 사용처 없음).
--   - 테스트(H2): spring.flyway.enabled=false 이므로 이 파일은 실행되지 않는다.

DROP TABLE IF EXISTS match_queue;
