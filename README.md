# BestDuo_BE

리그 오브 레전드 바텀 듀오(ADC + 서포터) 시너지 분석 서비스 **bestduo** 의 백엔드. Riot `league-v4` / `match-v5` API에서 에메랄드 이상 솔로 랭크 매치를 수집·저장하고, 패치 단위로 듀오 조합의 승률·픽률·랭킹·매치업 카운터를 집계해 REST로 제공합니다. 모든 데이터는 Riot 공식 API에서만 수집하며 순수 통계 제공 목적으로만 사용합니다.

- **Stack**: Spring Boot 4 · Java 21 · Gradle · JPA/Hibernate · PostgreSQL · Micrometer + Prometheus / Grafana
- **Architecture**: 3-Layer (presentation / application / infra) + 8 bounded contexts
- **External boundaries**: `RiotApiPort`, `ChampionMetaClient` 2개만 Port로 추상화

## Overview (English)

BestDuo_BE ingests solo-queue matches from Riot's APIs, stores raw match payloads, extracts bottom-lane duos, and serves patch-scoped synergy statistics.

### Key Capabilities
- **Seed → Collect → Ingest pipeline** that respects Riot request budgets and resumes from failure via `DailyPipelineState` + `MatchQueue` status.
- **Raw-first persistence**: match-v5 JSON is saved before domain-specific parsing so downstream aggregators can replay.
- **Bottom duo aggregation**: synergy scores, tier-scoped rankings, and matchup counters are written to materialized tables.
- **Public REST APIs** (`/bottom-duo/stats`, `/bottom-duo/matchups`, `/bottom-duo/counters`) consumed by the frontend.
- **Admin tooling** (`/admin/queue`, `/admin/aggregate`, `/admin/patch`, `/admin/coverage`, `/ingest`, `/seed`) for manual orchestration and inspection.

### High-Level Flow
1. `DailyLeagueEntriesRunner` pulls league entries by tier/division and registers new summoner seeds.
2. `CollectMatchIdsRunner` fetches recent match IDs and `MatchQueueEnqueuer` pushes them into `match_queue`.
3. `PipelineRunner` → `MatchIngestRunner` → `MatchQueueDispatcher` loads match-v5 payloads, `MatchSaver` / `BottomDuoRawSaver` persist them, and new participants are enqueued for expansion.
4. `BottomDuoStatAggregator` / `BottomDuoMatchupAggregator` compute tier-scoped stats into aggregate tables.
5. `GetBottomDuo*` use cases resolve the current patch / champion metadata and expose insights through controllers.

### API Surface (Selected)
| Method | Path | Purpose |
|---|---|---|
| `GET` | `/bottom-duo/stats` | Global win/pick/ban, ranking, tier delta (filter by tier, patch, champions) |
| `GET` | `/bottom-duo/matchups` | Duo-vs-duo matchup breakdown |
| `GET` | `/bottom-duo/counters` | Lowest win-rate counters for a selected duo |
| `POST` | `/ingest/match/{matchId}` | Manual match ingestion with tier label |
| `POST` | `/seed/bootstrap` | Register seed summoners from a league entry page |
| `GET` | `/admin/queue/stats` | MatchQueue status/error/retry counts |
| `POST` | `/admin/queue/work` | Dispatch pending matches |
| `POST` | `/admin/aggregate/bottom-duo-stat` | Trigger stat aggregation |
| `POST` | `/admin/aggregate/bottom-duo-matchup` | Trigger matchup aggregation |
| `GET` / `POST` | `/admin/patch(/current)` | Inspect or update the current patch |
| `GET` | `/admin/coverage[/{id}]` | Inspect collection coverage buckets |

### Architecture & Monitoring
- **3-Layer (presentation → application → infra)**: single-direction dependency graph. DB 접근은 `infra/persistence`의 단일 Service 클래스가 JPA Repository를 직접 사용하며, `*Impl` 접미사와 불필요한 Port 추상화는 제거되었습니다. 자세한 의사결정은 [ADR-002](./docs/adr_3layer_transition.md).
- **Bounded contexts**: `pipeline`, `ingest`, `aggregate`, `coverage`, `leagueentry`, `common`, `monitoring`, `config`. 모듈 성격에 따라 `presentation/application/infra` 중 필요한 폴더만 둡니다 (YAGNI).
- **Ports (2)**: `RiotApiPort`(Riot API), `ChampionMetaClient`(DataDragon). 외부 HTTP 경계만 추상화.
- **Rate limiting**: `DualWindowRateLimiter` + `RiotRateLimitInterceptor` 로 Riot API 레이트 리밋 준수.
- **Observability**: Actuator + Micrometer + Prometheus 로 큐 길이, 수집 처리량, JVM/SQL 지표 노출. Grafana 대시보드 구성은 `monitoring/` 참고.

## 시스템 개요 (Korean)

BestDuo_BE는 Riot `league-v4`/`match-v5` API에서 에메랄드 이상 솔로 랭크 매치를 수집해 저장하고, 패치 단위로 ADC/서포터 조합의 승률·픽률·랭킹·카운터 데이터를 집계해 REST로 제공하는 Spring Boot 4 / Java 21 백엔드입니다.

### 주요 기능
- Riot 요청 예산을 지키는 **Seed → Collect → Ingest 파이프라인** (`DailyPipelineState` + `MatchQueue.status` 조합으로 실패 재시도 및 증분 수집).
- **Raw-first 저장**: match-v5 JSON을 가공 전에 먼저 저장해 재집계 및 재처리에 사용.
- **바텀 듀오 집계**: 티어·패치 단위 시너지 점수, 랭킹, 매치업 카운터를 aggregate 테이블로 구성.
- **공개 REST API**: `/bottom-duo/stats`, `/bottom-duo/matchups`, `/bottom-duo/counters`.
- **관리 도구**: `/admin/queue`, `/admin/aggregate`, `/admin/patch`, `/admin/coverage`, `/ingest`, `/seed` 로 수동 운영·검증.

### 처리 흐름
1. `DailyLeagueEntriesRunner` 가 티어/디비전별 리그 엔트리를 가져와 신규 시드를 등록.
2. `CollectMatchIdsRunner` 가 최근 match ID를 조회하고 `MatchQueueEnqueuer` 가 `match_queue` 에 적재.
3. `PipelineRunner` → `MatchIngestRunner` → `MatchQueueDispatcher` 가 match-v5 payload를 불러오면 `MatchSaver` / `BottomDuoRawSaver` 가 저장하고, 새로 발견된 참가자는 확장 큐에 추가.
4. `BottomDuoStatAggregator` / `BottomDuoMatchupAggregator` 가 티어별 통계를 계산해 집계 테이블에 반영.
5. `GetBottomDuo*` 유스케이스가 현재 패치·챔피언 메타를 resolve 한 뒤 컨트롤러로 응답.

### 아키텍처
- **3-Layer (presentation → application → infra)**: 단방향 의존. JPA Repository 는 `infra/persistence` 의 단일 Service 가 직접 호출하며, 외부 HTTP 경계 2개(`RiotApiPort`, `ChampionMetaClient`) 에만 Port 를 둡니다. 결정 근거는 [ADR-002](./docs/adr_3layer_transition.md) 참고.
- **8 바운디드 컨텍스트**: `pipeline`, `ingest`, `aggregate`, `coverage`, `leagueentry`, `common`, `monitoring`, `config`. 모듈 성격에 맞춰 3-Layer 중 필요한 폴더만 둡니다.
- **관측**: Actuator + Micrometer + Prometheus (`monitoring/` 디렉터리에 Grafana 대시보드·Docker Compose 포함).
- **레이트 리밋**: `DualWindowRateLimiter` + `RiotRateLimitInterceptor`.

## 실행

```bash
./gradlew bootRun        # 애플리케이션 실행
./gradlew test           # 테스트 실행
./gradlew build          # 빌드
```

모니터링 스택(Prometheus + Grafana)은 `monitoring/run.sh` 또는 `monitoring/docker-compose.yml` 로 기동합니다.

## 참고 문서

- [시스템 아키텍처](./docs/architecture.md)
- [ADR-002 — 3-Layer 전환 (현행)](./docs/adr_3layer_transition.md)
- [ADR-001 — 경량 헥사고날 (Superseded)](./docs/adr_phase5_lightweight_hexagonal.md)
- [3-Layer 전환 계획서](./docs/refactoring_to_3layer_plan.md)
- [Bottom Duo Raw Pipeline 설계](./docs/bottom_duo_raw_pipeline_plan.md)
- [Daily Pipeline Redesign](./docs/daily_pipeline_redesign_plan.md)
- [Coverage Bucket Throughput 설계](./docs/coverage_bucket_throughput_plan.md)
