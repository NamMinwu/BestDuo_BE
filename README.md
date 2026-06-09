# BestDuo_BE

리그 오브 레전드 바텀 듀오(ADC + 서포터) 시너지 분석 서비스 **bestduo** 의 백엔드. Riot `league-v4` / `match-v5` API에서 에메랄드 이상 솔로 랭크 매치를 수집·저장하고, 패치 단위로 듀오 조합의 승률·픽률·랭킹·매치업 카운터를 집계해 REST로 제공합니다. 모든 데이터는 Riot 공식 API에서만 수집하며 순수 통계 제공 목적으로만 사용합니다.

- **Stack**: Spring Boot 4 · Java 21 · Gradle · JPA/Hibernate · PostgreSQL · Cloudflare R2 (S3-compatible) · Micrometer + Prometheus / Grafana
- **Architecture**: 3-Layer (presentation / application / infra) + 9 bounded contexts
- **External boundaries**: `RiotApiPort`, `ChampionMetaClient` 2개만 Port로 추상화

## Overview (English)

BestDuo_BE ingests solo-queue matches from Riot's APIs, stores raw match payloads, extracts bottom-lane duos, and serves patch-scoped synergy statistics.

### Key Capabilities
- **Seed → Collect → Ingest pipeline** that respects Riot request budgets and resumes from failure via `DailyPipelineState` + idempotent `match`-table dedup (no intermediate queue).
- **Raw-first persistence**: match-v5 JSON is saved before domain-specific parsing so downstream aggregators can replay.
- **Bottom duo aggregation from raw payload**: synergy scores, tier-scoped rankings, and matchup counters are computed by re-reading `match.payload_json` (no intermediate raw table — see [ADR-006](./docs/adr_aggregate_from_match_payload.md)).
- **Public REST APIs** (`/bottom-duo/stats`, `/bottom-duo/matchups`, `/bottom-duo/counters`) consumed by the frontend.
- **Admin tooling** (`/admin/patch`, `/admin/coverage`, `/admin/archive`, `/ingest`, `/seed`) for manual orchestration and inspection.
- **Cold storage offload**: older patches' match payloads can be archived to S3-compatible object storage (Cloudflare R2) and removed from the hot DB with a safety guard protecting the latest 2 patches (see [ADR-007](./docs/adr_archive_oom_projection.md)).

### High-Level Flow
1. `DailyLeagueEntriesRunner` pulls league entries by tier/division and registers new summoner seeds.
2. `CollectMatchIdsRunner` fetches recent match IDs per seed and ingests each new match inline — `IngestMatchDetail` loads the match-v5 payload and `MatchSaver` persists it with `collection_tier` (dedup via `match.existsById`, no intermediate queue).
3. `BottomDuoAggregateScheduler` runs daily — `AggregateBottomDuoFromMatch` re-reads `match.payload_json` per (patch, tier) and upserts stat + matchup tables in a single pass.
4. `GetBottomDuo*` use cases resolve the current patch / champion metadata and expose insights through controllers.
5. (On-demand) `MatchArchiver` streams `match.payload_json` to R2 as gzipped JSONL via interface projection + temp file streaming; `MatchPayloadCleaner` deletes archived rows after R2 `HEAD` verification.

### API Surface (Selected)
| Method | Path | Purpose |
|---|---|---|
| `GET` | `/bottom-duo/stats` | Global win/pick/ban, ranking, tier delta (filter by tier, patch, champions) |
| `GET` | `/bottom-duo/matchups` | Duo-vs-duo matchup breakdown |
| `GET` | `/bottom-duo/counters` | Lowest win-rate counters for a selected duo |
| `POST` | `/ingest/match/{matchId}` | Manual match ingestion with tier label |
| `POST` | `/seed/bootstrap` | Register seed summoners from a league entry page |
| `GET` / `POST` | `/admin/patch(/current)` | Inspect or update the current patch |
| `GET` | `/admin/coverage[/{id}]` | Inspect collection coverage buckets |
| `POST` | `/admin/archive/match-payload` | Archive `match.payload_json` to R2 cold storage by (patch, tiers) |
| `POST` | `/admin/archive/cleanup-archived` | Delete archived match rows after R2 HEAD verification (protects latest 2 patches) |

### Architecture & Monitoring
- **3-Layer (presentation → application → infra)**: single-direction dependency graph. DB 접근은 `infra/persistence`의 단일 Service 클래스가 JPA Repository를 직접 사용하며, `*Impl` 접미사와 불필요한 Port 추상화는 제거되었습니다. 자세한 의사결정은 [ADR-002](./docs/adr_3layer_transition.md).
- **Bounded contexts (9)**: `pipeline`, `ingest`, `aggregate`, `archive`, `coverage`, `leagueentry`, `common`, `monitoring`, `config`. 모듈 성격에 따라 `presentation/application/infra` 중 필요한 폴더만 둡니다 (YAGNI).
- **Ports (2)**: `RiotApiPort`(Riot API), `ChampionMetaClient`(DataDragon). 외부 HTTP 경계만 추상화.
- **Rate limiting**: `DualWindowRateLimiter` + `RiotRateLimitInterceptor` 로 Riot API 레이트 리밋 준수.
- **Observability**: Actuator + Micrometer + Prometheus 로 수집 백로그, 수집 처리량, JVM/SQL 지표 노출. Grafana 대시보드 구성은 `monitoring/` 참고.
- **Hot/Cold storage**: Hot data 는 PostgreSQL, 과거 patch 의 `match.payload_json` 은 R2 (S3-compatible) 로 archive — interface projection + temp file streaming 으로 대용량 OOM 회피 ([ADR-007](./docs/adr_archive_oom_projection.md)).

## 시스템 개요 (Korean)

BestDuo_BE는 Riot `league-v4`/`match-v5` API에서 에메랄드 이상 솔로 랭크 매치를 수집해 저장하고, 패치 단위로 ADC/서포터 조합의 승률·픽률·랭킹·카운터 데이터를 집계해 REST로 제공하는 Spring Boot 4 / Java 21 백엔드입니다.

### 주요 기능
- Riot 요청 예산을 지키는 **Seed → Collect → Ingest 파이프라인** (`DailyPipelineState` + `match` 테이블 멱등 dedup 으로 실패 재시도·중복 없는 증분 수집).
- **Raw-first 저장**: match-v5 JSON을 가공 전에 먼저 저장해 재집계 및 재처리에 사용.
- **`match.payload_json` 기반 in-memory 집계**: 별도 raw 테이블 없이 `match.payload_json` 을 재읽어 티어·패치 단위 시너지·랭킹·매치업 카운터를 한 패스로 계산 ([ADR-006](./docs/adr_aggregate_from_match_payload.md)).
- **공개 REST API**: `/bottom-duo/stats`, `/bottom-duo/matchups`, `/bottom-duo/counters`.
- **관리 도구**: `/admin/patch`, `/admin/coverage`, `/admin/archive`, `/ingest`, `/seed` 로 수동 운영·검증.
- **Cold storage 분리**: 과거 patch 의 `match.payload_json` 을 R2(S3 호환) 로 archive + hot DB 에서 제거. 진행 중인 최신 2 patch 자동 보호 ([ADR-007](./docs/adr_archive_oom_projection.md)).

### 처리 흐름
1. `DailyLeagueEntriesRunner` 가 티어/디비전별 리그 엔트리를 가져와 신규 시드를 등록.
2. `CollectMatchIdsRunner` 가 시드별 최근 match ID를 조회해 새 매치를 inline 으로 ingest — `IngestMatchDetail` 가 match-v5 payload를 불러오면 `MatchSaver` 가 `collection_tier` 와 함께 저장 (dedup 은 `match.existsById`, 별도 큐 없음).
3. `BottomDuoAggregateScheduler` 가 일 1회 실행 — `AggregateBottomDuoFromMatch` 가 (patch, tier) 단위로 `match.payload_json` 을 재읽어 stat + matchup 을 단일 패스로 upsert.
4. `GetBottomDuo*` 유스케이스가 현재 패치·챔피언 메타를 resolve 한 뒤 컨트롤러로 응답.
5. (on-demand) `MatchArchiver` 가 interface projection + temp file streaming 으로 R2 에 gzip JSONL 업로드, `MatchPayloadCleaner` 가 R2 `HEAD` 검증 후 match 행 삭제.

### 아키텍처
- **3-Layer (presentation → application → infra)**: 단방향 의존. JPA Repository 는 `infra/persistence` 의 단일 Service 가 직접 호출하며, 외부 HTTP 경계 2개(`RiotApiPort`, `ChampionMetaClient`) 에만 Port 를 둡니다. 결정 근거는 [ADR-002](./docs/adr_3layer_transition.md) 참고.
- **9 바운디드 컨텍스트**: `pipeline`, `ingest`, `aggregate`, `archive`, `coverage`, `leagueentry`, `common`, `monitoring`, `config`. 모듈 성격에 맞춰 3-Layer 중 필요한 폴더만 둡니다.
- **관측**: Actuator + Micrometer + Prometheus (`monitoring/` 디렉터리에 Grafana 대시보드·Docker Compose 포함).
- **레이트 리밋**: `DualWindowRateLimiter` + `RiotRateLimitInterceptor`.
- **Hot/Cold storage**: Hot 은 PostgreSQL, 과거 patch payload 는 R2 cold storage. Hibernate L1 캐시 우회를 위해 interface projection + temp file streaming 으로 대용량 처리 ([ADR-007](./docs/adr_archive_oom_projection.md)).

## 실행

```bash
./gradlew bootRun        # 애플리케이션 실행
./gradlew test           # 테스트 실행
./gradlew build          # 빌드
```

모니터링 스택(Prometheus + Grafana)은 `monitoring/run.sh` 또는 `monitoring/docker-compose.yml` 로 기동합니다.

## 참고 문서

- [시스템 아키텍처](./docs/architecture.md)
- [ADR-001 — 경량 헥사고날 (Superseded)](./docs/adr_phase5_lightweight_hexagonal.md)
- [ADR-002 — 3-Layer 전환 (현행)](./docs/adr_3layer_transition.md)
- [ADR-004 — 듀오 랭킹 점수 산출 (게임수 비중 강화)](./docs/adr_bottom_duo_ranking_game_weight.md)
- [ADR-005 — 카운터 추천 정렬 (베이지안 스무딩)](./docs/adr_counter_bayesian_smoothing.md)
- [ADR-006 — cron 집계 경로를 match.payload_json 기반 in-memory 누적으로 통일](./docs/adr_aggregate_from_match_payload.md)
- [ADR-007 — Archive endpoint OOM 해결 (L1 캐시 우회 projection + temp file 스트리밍)](./docs/adr_archive_oom_projection.md)
- [3-Layer 전환 계획서](./docs/refactoring_to_3layer_plan.md)
- [Daily Pipeline Redesign](./docs/daily_pipeline_redesign_plan.md)
- [Coverage Bucket Throughput 설계](./docs/coverage_bucket_throughput_plan.md)
