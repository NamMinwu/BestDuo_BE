-- V6: match_queue.priority 컬럼 제거.
--
-- 배경:
--   - priority 컬럼은 모든 enqueue에서 동일한 값(PipelineProperties.collectPriority = 50)
--     으로만 들어가, ORDER BY mq.priority ASC 가 사실상 ORDER BY mq.updated_at ASC
--     와 동일하게 동작하는 죽은 우선순위였다.
--   - Stage 3 우선 처리는 priorityTier(WHERE collection_tier = ?) 로 동작하며,
--     match_queue.priority 와 무관하다.
--   - YAGNI 원칙에 따라 차별화되지 않는 컬럼을 제거하여 스키마와 쿼리를 단순화한다.
--
-- 동작:
--   - priority 컬럼을 drop 한다. NOT NULL 이지만 코드에서 더 이상 read/write 하지
--     않으므로 운영 영향 없음. 관련 쿼리(ORDER BY mq.priority asc) 는 동일 PR 에서
--     함께 제거된다.

ALTER TABLE match_queue DROP COLUMN IF EXISTS priority;
