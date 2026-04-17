-- V2: summoner, match_queue 두 테이블을 drop 후 재생성한다.
--
-- 배경:
--   운영 DB 의 summoner / match_queue 데이터를 초기화하면서, 동시에 Flyway 로
--   스키마를 명시적으로 기술한 첫 마이그레이션을 남기기 위한 스크립트.
--   V1 은 비어있는 baseline marker 였고, 이 파일부터 실제 스키마가 코드로 관리된다.
--
-- 대상 환경:
--   - 운영(Railway): V1 baseline 이후 이 스크립트가 실행되어 두 테이블을 재생성한다.
--   - 새 환경(fresh DB): DROP IF EXISTS 덕분에 안전하게 CREATE 까지 이어진다.
--   - 테스트(H2): spring.flyway.enabled=false 이므로 이 파일은 실행되지 않는다.
--
-- 컬럼 타입은 Hibernate 6.x PostgreSQL Dialect 의 기본 매핑을 따른다.
--   String             -> varchar(255)
--   Long               -> bigint
--   int                -> integer
--   OffsetDateTime     -> timestamp(6) with time zone
--   @Enumerated(STRING)-> varchar(255)

DROP TABLE IF EXISTS summoner;
DROP TABLE IF EXISTS match_queue;

CREATE TABLE summoner (
    puuid                   varchar(255)                    NOT NULL,
    last_match_start_time   bigint,
    last_known_tier         varchar(255),
    tier_observed_at        timestamp(6) with time zone,
    seeded_at               timestamp(6) with time zone,
    match_ids_collected_at  timestamp(6) with time zone,
    created_at              timestamp(6) with time zone     NOT NULL,
    updated_at              timestamp(6) with time zone     NOT NULL,
    PRIMARY KEY (puuid)
);

CREATE TABLE match_queue (
    match_id         varchar(255)                 NOT NULL,
    status           varchar(255)                 NOT NULL,
    priority         integer                      NOT NULL,
    collection_tier  varchar(255)                 NOT NULL,
    patch            varchar(255),
    retry_count      integer                      NOT NULL,
    last_error       varchar(255),
    locked_at        timestamp(6) with time zone,
    created_at       timestamp(6) with time zone  NOT NULL,
    updated_at       timestamp(6) with time zone  NOT NULL,
    PRIMARY KEY (match_id)
);
