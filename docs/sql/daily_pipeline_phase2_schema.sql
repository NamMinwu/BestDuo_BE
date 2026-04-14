-- Phase 2: DB 스키마 변경 스크립트
-- 모든 새 컬럼은 nullable로 추가 → ALTER TABLE ADD COLUMN 무중단 적용 가능

-- ── 1. summoner 테이블: Stage 1/2 추적 컬럼 추가 ─────────────────────────────
ALTER TABLE summoner
    ADD COLUMN IF NOT EXISTS seeded_at            TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS match_ids_collected_at TIMESTAMPTZ;

-- Stage 2 대상 조회 인덱스 (seeded_at DESC, match_ids_collected_at)
CREATE INDEX IF NOT EXISTS idx_summoner_stage2_targets
    ON summoner (seeded_at DESC)
    WHERE seeded_at IS NOT NULL;

-- ── 2. coverage_bucket 테이블: 일일 seed 완료 추적 컬럼 추가 ─────────────────
ALTER TABLE coverage_bucket
    ADD COLUMN IF NOT EXISTS daily_seed_completed  BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS daily_seed_reset_at   TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS daily_seed_steps_processed INTEGER NOT NULL DEFAULT 0;

-- ── 3. daily_pipeline_state 테이블 신규 생성 ─────────────────────────────────
CREATE TABLE IF NOT EXISTS daily_pipeline_state (
    id                     BIGSERIAL     PRIMARY KEY,
    pipeline_date          DATE          NOT NULL,
    seed_api_calls_used    INTEGER       NOT NULL DEFAULT 0,
    collect_api_calls_used INTEGER       NOT NULL DEFAULT 0,
    seed_completed_tiers   VARCHAR(1024) NOT NULL DEFAULT '[]',
    created_at             TIMESTAMPTZ   NOT NULL,
    updated_at             TIMESTAMPTZ   NOT NULL,

    CONSTRAINT uk_daily_pipeline_state_date UNIQUE (pipeline_date)
);
