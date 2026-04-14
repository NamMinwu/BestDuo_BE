# Daily Data Collection Pipeline Redesign Plan

## Overview

현재 시스템은 `CoverageBucket` 기반의 목표 매치 수 달성 모델로, `SEED_SUMMONERS` / `REFRESH_SUMMONERS` / `INGEST_MATCH_DETAIL` 세 가지 WorkItem을 스케줄링한다. 이 계획은 이를 일일 운영 파이프라인으로 재설계한다:

1. **Stage 1 — Summoner 매일 최신화**: league entries에서 summoner 등록/갱신
2. **Stage 2 — Summoner → Match Queue**: 최근 갱신된 summoner의 matchIds 수집
3. **Stage 3 — Match Queue → 상세 정보 (상시 실행)**: match_queue에서 상세 정보 수집

핵심 변경: WorkItem 추상화 전체 제거, Refresh 로직 제거, 10명 증폭 로직 제거, tier/patch 기반 우선순위 도입, 단일 스레드 직접 실행 모델로 단순화.

---
[daily_pipeline_redesign_plan.md](daily_pipeline_redesign_plan.md)
## 아키텍처 Trade-off 분석 및 선택 이유

### 0. 전체 아키텍처 접근 방식 비교

파이프라인을 구성하는 방법에는 세 가지 주요 선택지가 있다.

#### Option A: Spring Batch

Spring Boot 생태계의 표준 배치 프레임워크. `Job → Step → ItemReader/Processor/Writer` 구조로 배치 처리를 정형화한다.

| 장점 | 단점 |
|------|------|
| Job 재시작, skip, retry 기본 제공 | **Stage 3는 종료가 없는 상시 루프** — Spring Batch는 Job 완료를 전제로 설계됨 |
| `JobRepository`로 실행 이력 자동 관리 | `JobRepository`를 위한 6개 테이블 추가 (`BATCH_JOB_INSTANCE` 등) |
| 청크 단위 처리 + 트랜잭션 자동 관리 | chunk 처리 도중 429 발생 시 rollback/재시도 처리 복잡 |
| Spring 생태계와 자연스러운 통합 | Stage 1→2→3 **동적 우선순위 전환**을 Step DAG으로 표현하기 어려움 |
| | Rate limit(20/1s)이 핵심 제약인데, chunk 크기 모델과 개념이 불일치 |
| | 상태 영속화가 `DailyPipelineState` 하나로 충분한데 6개 테이블 오버헤드 |

**부적합 이유:** Spring Batch는 "실행 → 완료"하는 정형 배치에 강하다. 이 파이프라인의 핵심은 "상시 실행 + 동적 우선순위 + rate limit 즉각 반응"으로, 설계 철학이 근본적으로 다르다.

---

#### Option B: WorkItem + DB Job Queue (기존 시스템)

스케줄러가 DB에 WorkItem을 INSERT → 워커가 DB에서 꺼내서 실행하는 2단계 구조.

| 장점 | 단점 |
|------|------|
| 멀티 워커 수평 확장 가능 | **단일 가상 스레드만 사용** — 분산 처리 이점 없이 복잡도만 추가 |
| DB로 작업 상태 가시성 확보 | 스케줄러(emit) → DB → 워커(pick) 3단계 간접 계층 |
| 재시작 복구 (RUNNING → PENDING) | Stage 1/2 상태는 이미 `DailyPipelineState`로 영속화 가능 |
| | `match_queue`가 이미 영속 큐인데 `work_item`이 이중 큐 |
| | 매 사이클 `work_item` INSERT/UPDATE DB 부하 |

**부적합 이유:** 멀티 워커 분산 환경을 위한 설계인데, 현재와 앞으로도 단일 스레드 운영이 전제다. 이점 없이 복잡도만 높인다.

---

#### Option C: PipelineRunner 직접 실행 루프 (선택)

단일 가상 스레드가 직접 Stage 1 → 2 → 3 우선순위를 판단하고 각 실행 클래스를 직접 호출한다.

| 장점 | 단점 |
|------|------|
| 단순한 루프 — if/else로 우선순위 명확히 표현 | 멀티 워커 수평 확장 불가 (현재 요구사항 없음) |
| 상시 실행(Stage 3)을 자연스럽게 표현 | `work_item` 테이블 기반 작업 가시성 없어짐 |
| 429 시 즉시 sleep, 회복 즉시 재개 | → `DailyPipelineState` + `match_queue` 통계로 대체 가능 |
| DB 쓰기 최소화 (WorkItem emit/markDone 제거) | |
| 재시작 복구를 `DailyPipelineState` 하나로 통합 | |
| 추가 의존성/스키마 없음 | |

**선택 이유:** 세 가지 제약 — 단일 API key, 상시 실행 Stage 3, 동적 우선순위 전환 — 을 가장 직접적으로 표현하는 구조다. Spring Batch나 WorkItem은 이 세 가지 중 하나도 자연스럽게 다루지 못한다.

---

#### 전체 아키텍처 비교 요약

| 항목 | Spring Batch | WorkItem (기존) | PipelineRunner (선택) |
|------|-------------|-----------------|----------------------|
| 상시 실행 Stage 3 | 부적합 | 가능하나 어색 | 자연스럽게 표현 |
| 429 즉각 대응 | chunk rollback 복잡 | markPending 후 재시도 | sleep → continue |
| 동적 우선순위 전환 | Step DAG으로 표현 어려움 | 스케줄러 로직 분산 | 루프 한 곳에 집중 |
| 재시작 복구 | JobRepository 자동 | RUNNING→PENDING 복구 | DailyPipelineState |
| 추가 DB 테이블 | 6개 (JobRepository) | 1개 (work_item) | 1개 (DailyPipelineState) |
| 수평 확장 | 가능 | 가능 | 불가 (현재 불필요) |
| 코드 복잡도 | 높음 (인터페이스 다수) | 중간 | 낮음 |

---

### 1. WorkItem 제거: DB 기반 Job Queue vs. 직접 실행 루프 (선택)

#### Option A: WorkItem 유지 (기존)
스케줄러가 "무엇을 할지" DB에 INSERT → 워커가 DB에서 꺼내서 실행하는 2단계 구조.

| 장점 | 단점 |
|------|------|
| 분산 환경 / 멀티 워커 확장 가능 | **지금은 단일 가상 스레드 하나뿐** — 과도한 추상화 |
| 작업 상태 관찰 용이 (PENDING/RUNNING/DONE) | WorkItem emit → pickAndLock → execute 3단계 간접 계층 |
| 재시작 복구 (RUNNING → PENDING) | Stage 1/2 상태는 이미 DailyPipelineState + CoverageBucket에 영속화 |
| | Stage 3는 match_queue가 이미 영속 큐 역할 — 이중 큐 |
| | work_item 테이블에 매 사이클 INSERT/UPDATE 부하 |

#### Option B: 직접 실행 루프 (선택)
`PipelineRunner` 단일 스레드가 직접 "Stage 1 → 2 → 3" 우선순위를 판단하고 실행.

| 장점 | 단점 |
|------|------|
| 코드 계층 단순화 (workitem 패키지 전체 제거) | 멀티 워커로 수평 확장 불가 (현재 요구사항 없음) |
| DB 쓰기 감소 (WorkItem emit/markDone 제거) | work_item 테이블 기반 관측성 사라짐 |
| 재시작 복구를 DailyPipelineState 하나로 통합 | → DailyPipelineState + match_queue 통계로 대체 가능 |
| Stage 1/2/3 우선순위 로직이 한 곳에 모임 | |

**선택 이유:** 현재 아키텍처는 단일 가상 스레드 하나로 모든 작업을 처리한다. WorkItem은 멀티 워커 분산 환경을 위한 설계인데, 그 이점을 전혀 활용하지 못하면서 복잡도만 추가한다. 상태 영속화는 DailyPipelineState와 match_queue가 이미 담당하므로 WorkItem 없이도 재시작 복구가 가능하다.

---

### 2. Refresh 제거: Refresh vs. 새 Summoner 순회 (선택)

| 항목 | Refresh 유지 | 새 Summoner 순회 (선택) |
|------|-------------|------------------------|
| API 소비 효율 | 이미 수집된 summoner 재확인 → 변동 없으면 낭비 | 새 summoner 유입 → 모집단 확대 |
| tier 신뢰성 | 최근 확인 시점 추적 가능 | `seeded_at` 컬럼으로 동등하게 추적 가능 |
| 복잡도 | SEED + REFRESH 두 단계 유사 로직 중복 | 단일 SEED 단계로 통합 |

**선택 이유:** 같은 API 비용으로 새 summoner를 수집하면 모집단이 넓어지고 match_queue 유입도 증가한다. tier 신선도는 `seeded_at` 추적으로 동등하게 보장된다.

---

### 3. 10명 증폭 제거: 증폭 vs. summoner 직접 순회 (선택)

| 항목 | 10명 증폭 유지 | summoner 직접 순회 (선택) |
|------|---------------|--------------------------|
| tier 신뢰성 | 같은 match의 다른 참여자 → tier 불명확 | summoner의 확인된 tier로 명확하게 태깅 |
| 데이터 품질 | tier 불명확한 match가 queue에 섞임 | seeded tier 기반 확실한 tier 태깅 |

**선택 이유:** match_queue의 tier 컬럼 신뢰성이 분석 품질의 핵심이다. summoner를 직접 순회하면 tier가 명확한 match만 수집된다.

---

### 4. Rate Limit 배분: Time-Slicing vs. Priority-Based Daily Budget Cap (선택)

| 항목 | Time-Slicing | Budget Cap (선택) |
|------|-------------|-------------------|
| Stage 3 API 활용 | Stage 1/2 시간대에 낭비 발생 | Stage 1/2 끝나면 남은 예산 전부 Stage 3 |
| 재시작 복구 | 시간대 상태 복구 복잡 | DailyPipelineState 하나로 복구 |
| 구현 | 단순 | DailyBudgetTracker 컴포넌트 추가 필요 |

**선택 이유:** Stage 3는 데이터 생산의 핵심이므로 최대한 많은 API를 써야 한다. Budget Cap 방식은 Stage 1/2가 얼마나 빨리 끝나든 남은 예산이 전부 Stage 3로 흘러간다.

---

### 5. DIA/EME 순회: 무작위 vs. Division+Page 순차 순회 (선택)

| 항목 | 무작위 샘플링 | Division+Page 순차 (선택) |
|------|-------------|--------------------------|
| 모집단 커버리지 | 중복 처리 가능, 일부 누락 가능 | 전체 tier 구간 균등 커버 |
| 기존 인프라 활용 | 신규 설계 필요 | `CoverageBucket.seedPage/seedDivision` 재활용 |

**선택 이유:** 전체 division을 순차적으로 커버하며, 이미 존재하는 `seedPage/seedDivision` 필드를 그대로 활용 가능하다.

---

### 6. Stage 2 summoner 우선순위: FIFO vs. 최근 seeded 우선 (선택)

**선택 이유:** `seeded_at DESC` 정렬로 방금 tier가 확인된 summoner부터 matchIds를 수집한다. match_queue의 tier 태깅 정확도를 최대화한다.

---

### 7. Stage 3 우선순위: FIFO vs. Patch+Tier 기반 (선택)

**선택 이유:** 현재 patch + 고티어 데이터를 먼저 처리해야 분석 가치가 높다. FIFO는 과거 패치 데이터가 처리 대기열을 차지할 수 있다.

---

## 현재 구조 분석

### 제거 대상

| 대상 | 이유 |
|------|------|
| `workitem/` 패키지 전체 | WorkItem 추상화 제거 |
| `coverage/application/CoverageSchedulingService.java` | WorkItem emit 로직 — PipelineRunner로 대체 |
| `coverage/application/CoverageScheduler.java` | @Scheduled emit 트리거 — PipelineRunner 내부로 흡수 |
| `refresh/` 패키지 전체 | Refresh 개념 제거 |
| `IngestMatchDetail.expandParticipants()` | 10명 증폭 로직 제거 |
| `SummonerExpansionQueue` (인터페이스 + 구현) | 10명 증폭 로직 제거 |
| `LeagueEntriesRefreshLoaderImpl.java` | Refresh 제거 후 불필요 |

---

## 새 아키텍처 설계

### 파이프라인 흐름

```
PipelineRunner (단일 가상 스레드, 상시 실행)
  │
  ├─ [429 cooling?] → sleep(남은 시간) → continue
  │
  ├─ Stage 1: DailySeedRunner.hasWorkToday()?
  │    ├─ YES → DailySeedRunner.runNextChunk()
  │    │         └─ SeedBootstrapExecutor.execute() 직접 호출
  │    │             └─ seededAt 갱신, DailyPipelineState.seedApiCallsUsed++
  │    │
  │    └─ Stage 2: DailyBudgetTracker.canCollect() && CollectMatchIdsRunner.hasPending()?
  │         ├─ YES → CollectMatchIdsRunner.runBatch()
  │         │         └─ summoner 쿼리 (seeded_at DESC, 미수집 우선)
  │         │             └─ matchIds API 호출 → match_queue enqueue
  │         │                 └─ matchIdsCollectedAt 갱신
  │         │
  │         └─ Stage 3: MatchIngestWorker.execute() 직접 호출
  │                      └─ match_queue에서 patch+tier 우선순위로 pick
  │                          └─ match API 호출 → bottom_duo_raw 저장
  │
  └─ match_queue READY 없음 → sleep(pollingInterval)

┌─────────────────────────────────────────────────────┐
│ Shared: DualWindowRateLimiter (20/1s, 100/2min)     │
│         DailyBudgetTracker (Stage 1/2 일일 상한)     │
│         DailyPipelineState (날짜별 진행 상태 영속화) │
└─────────────────────────────────────────────────────┘
```

### 새 클래스 구조

| 클래스 | 패키지 | 역할 | 기존 대응 |
|--------|--------|------|-----------|
| `PipelineRunner` | `pipeline/application/` | 단일 스레드 루프. Stage 1→2→3 우선순위 판단 + 실행 | `WorkerPool` + `CoverageSchedulingService` + `CoverageScheduler` 통합 대체 |
| `DailySeedRunner` | `pipeline/application/` | Stage 1 실행. GM/M/C 전체, DIA/EME 구간 순회. seededAt 갱신 | `SeedPageWorker` + seed 관련 `CoverageSchedulingService` 로직 |
| `CollectMatchIdsRunner` | `pipeline/application/` | Stage 2 실행. summoner 직접 쿼리 → matchIds → match_queue enqueue | `RefreshSummonerWorker` 대체 (WorkItem 없이) |
| `DailyBudgetTracker` | `common/infra/riot/budget/` | Stage 1/2 일일 API 호출 상한 추적. 자정 리셋 | 신규 |
| `DailyPipelineState` (엔티티) | `common/infra/persistence/entity/` | 날짜별 파이프라인 진행 상태 DB 영속화 | 신규 |

### 기존 클래스 유지

| 클래스 | 역할 | 변경 |
|--------|------|------|
| `SeedBootstrapExecutor` | league entries → summoner 등록/갱신 | matchIds enqueue 제거, seededAt 갱신 추가 |
| `MatchIngestWorker` | match_queue → Riot API → bottom_duo_raw | 직접 호출 (IngestMatchDetailWorker 래퍼 제거) |
| `IngestMatchDetail` | match 1개 상세 수집 | expandParticipants 제거 |
| `CoverageBucket` | DIA/EME division+page 순회 상태 | daily_seed_completed 컬럼 추가 |
| `DualWindowRateLimiter` | rate limit 공유 | 유지 |

---

## Rate Limit 할당 전략

### 일일 예산 계산

| 항목 | 값 |
|------|---|
| 단기 제한 | 20 req / 1초 |
| 장기 제한 | 100 req / 2분 (= 50 req/min) |
| 일일 총 예산 | 50 × 60 × 24 = 72,000 req |
| 실제 가용 (429 감안 90%) | ~64,800 req/day |

### Stage별 할당

| Stage | 일일 예산 상한 | 비율 | 근거 |
|-------|--------------|------|------|
| Stage 1 (SEED) | 2,000 req | ~3% | 1 page = 1 API 호출. DIA/EME 일일 구간 최대치 |
| Stage 2 (COLLECT_MATCH_IDS) | 8,000 req | ~12% | 하루 ~8,000명 summoner matchIds 수집 |
| Stage 3 (INGEST) | 나머지 전부 (~54,800) | ~85% | 데이터 생산의 핵심, 최대 투입 |

Stage 1/2 예산이 모두 소진되면 `PipelineRunner`는 Stage 3 전용 모드로 자동 전환.

---

## DB 스키마 변경

### summoner 테이블 추가 컬럼

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `seeded_at` | TIMESTAMPTZ | Stage 1에서 등록/갱신된 시점 |
| `match_ids_collected_at` | TIMESTAMPTZ | Stage 2에서 matchIds를 수집한 시점 |

Stage 2 대상 summoner 조회 (최근 seeded 우선):
```sql
SELECT * FROM summoner
WHERE seeded_at IS NOT NULL
  AND (match_ids_collected_at IS NULL OR match_ids_collected_at < seeded_at)
ORDER BY seeded_at DESC
LIMIT :batchSize;
```

### daily_pipeline_state 테이블 (신규)

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `id` | BIGINT PK | |
| `pipeline_date` | DATE UNIQUE | 해당 날짜 |
| `seed_api_calls_used` | INTEGER DEFAULT 0 | Stage 1 사용 API 호출 수 |
| `collect_api_calls_used` | INTEGER DEFAULT 0 | Stage 2 사용 API 호출 수 |
| `seed_completed_tiers` | VARCHAR | 오늘 seed 완료된 tier 목록 (JSON) |
| `created_at` | TIMESTAMPTZ | |
| `updated_at` | TIMESTAMPTZ | |

재시작 복구: `pipeline_date = TODAY` 행이 있으면 이어서 진행. 없으면 새 행 생성.

### coverage_bucket 테이블 추가 컬럼

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `daily_seed_completed` | BOOLEAN DEFAULT false | 오늘 해당 bucket의 seed 완료 여부 |
| `daily_seed_reset_at` | TIMESTAMPTZ | 마지막 자정 리셋 시점 |

---

## 단계별 구현 계획

### Phase 1: WorkItem 전체 제거 + Refresh/증폭 제거

| # | 작업 | 대상 | Risk |
|---|------|------|------|
| 1 | `workitem/` 패키지 전체 삭제 | `WorkItem`, `WorkItemDispatcher`, `WorkItemWorker`, `WorkerPool`, `WorkerContract`, `SeedPageWorker`, `RefreshSummonerWorker`, `IngestMatchDetailWorker`, `WorkItemType`, `WorkItemStatus` | Medium — 참조 정리 필요 |
| 2 | `CoverageSchedulingService` 삭제 | `coverage/application/CoverageSchedulingService.java` | Medium |
| 3 | `CoverageScheduler` 삭제 | `coverage/application/CoverageScheduler.java` | Low |
| 4 | `refresh/` 패키지 전체 삭제 | `RefreshBatchExecutor`, `RefreshSummonerMatches`, `RefreshController` 등 | Medium |
| 5 | `IngestMatchDetail`에서 10명 증폭 제거 | `ingest/application/IngestMatchDetail.java` | Low |
| 6 | `SummonerExpansionQueue` 삭제 | `common/application/port/`, `common/infra/persistence/` | Low |
| 7 | `LeagueEntriesRefreshLoaderImpl` 삭제 | `common/infra/riot/` | Low |
| 8 | `work_item` 테이블 DROP 마이그레이션 | SQL | Medium |

### Phase 2: DB 스키마 + Summoner 엔티티 확장

| # | 작업 | 대상 | Risk |
|---|------|------|------|
| 9 | Summoner 엔티티에 `seededAt`, `matchIdsCollectedAt` 추가 | `common/infra/persistence/entity/Summoner.java` | Low |
| 10 | SummonerJpaRepository에 Stage 2용 조회 쿼리 추가 | `common/infra/persistence/repository/SummonerJpaRepository.java` | Low |
| 11 | `DailyPipelineState` 엔티티 + Repository 생성 | 신규 2개 파일 | Low |
| 12 | `CoverageBucket`에 `dailySeedCompleted`, `dailySeedResetAt` 추가 | `coverage/infra/persistence/entity/CoverageBucket.java` | Low |
| 13 | DB 마이그레이션 스크립트 (새 컬럼, 새 테이블) | SQL | Low — 전부 nullable |

### Phase 3: Stage 1 — DailySeedRunner 구현

| # | 작업 | 대상 | Risk |
|---|------|------|------|
| 14 | `SeedBootstrapExecutor`에서 matchIds enqueue 제거, `seededAt` 갱신 추가 | `seed/application/SeedBootstrapExecutor.java` | Medium |
| 15 | `DailySeedRunner` 구현 | 신규 `pipeline/application/DailySeedRunner.java` | Medium |
| — | — GM/M/C: 매일 전체 리스트 수집. `daily_pipeline_state.seedCompletedTiers`에 완료 기록 | | |
| — | — DIA/EME: `CoverageBucket.seedPage/seedDivision` 기반 구간 순회. 일일 예산(2,000 req) 소진 시 중단 | | |
| — | — 자정 경계 감지: `dailySeedResetAt < TODAY` 이면 `dailySeedCompleted = false`로 리셋 | | |

### Phase 4: Stage 2 — CollectMatchIdsRunner 구현

| # | 작업 | 대상 | Risk |
|---|------|------|------|
| 16 | `CollectMatchIdsRunner` 구현 | 신규 `pipeline/application/CollectMatchIdsRunner.java` | Medium |
| — | — summoner 테이블에서 직접 쿼리 (seeded_at DESC, matchIdsCollectedAt 미수집 우선) | | |
| — | — tier별 `matchesPerSummoner` 차등 적용 (GM/M/C: 30, DIA/EME: 10) | | |
| — | — `matchIdsCollectedAt` 갱신, `DailyPipelineState.collectApiCallsUsed` 증가 | | |
| 17 | tier별 matchCount 설정 추가 | `config/PipelineProperties.java` (신규 또는 기존 확장), `application.yml` | Low |

### Phase 5: Stage 3 — INGEST 우선순위 조정

| # | 작업 | 대상 | Risk |
|---|------|------|------|
| 18 | match_queue pick 쿼리에 patch+tier 우선순위 적용 | `ingest/infra/persistence/MatchQueueDispatcherImpl.java` | Low |

### Phase 6: PipelineRunner 구현

| # | 작업 | 대상 | Risk |
|---|------|------|------|
| 19 | `DailyBudgetTracker` 구현 | 신규 `common/infra/riot/budget/DailyBudgetTracker.java` | Low |
| — | — `DailyPipelineState` 기반 Stage 1/2 일일 잔량 추적. 자정 자동 리셋 | | |
| 20 | `PipelineRunner` 구현 | 신규 `pipeline/application/PipelineRunner.java` | Medium |
| — | — `@PostConstruct`로 가상 스레드 시작 | | |
| — | — 429 감지 시 `durationUntilAvailable()` 만큼 sleep | | |
| — | — Stage 1 → 2 → 3 우선순위 판단 루프 | | |
| — | — Stage 1/2 예산 소진 시 "Stage 3 전용 모드" 전환 + 로그 | | |
| 21 | `application.yml` 업데이트 | `src/main/resources/application.yml` | Low |

### Phase 7: 테스트

| # | 작업 | 우선순위 |
|---|------|---------|
| 22 | `PipelineRunnerTest` — Stage 1→2→3 우선순위 판단, 예산 소진 시 전환 | High |
| 23 | `DailySeedRunnerTest` — GM/M/C 전체, DIA/EME 구간 순회, 자정 리셋 | High |
| 24 | `CollectMatchIdsRunnerTest` — summoner 우선순위, tier별 matchCount 차등 | High |
| 25 | `DailyBudgetTrackerTest` — 예산 소진/자정 리셋 | High |
| 26 | `SeedBootstrapExecutorTest` — matchIds enqueue 제거 후 동작 | Medium |
| 27 | 전체 파이프라인 통합 테스트 (Testcontainers) | Medium |

---

## 수정 대상 파일 요약

### 삭제
- `workitem/` 전체 패키지
- `coverage/application/CoverageSchedulingService.java`
- `coverage/application/CoverageScheduler.java`
- `refresh/` 전체 패키지
- `common/application/port/SummonerExpansionQueue.java`
- `common/infra/persistence/SummonerExpansionQueueImpl.java`
- `common/infra/riot/LeagueEntriesRefreshLoaderImpl.java`

### 수정
- `seed/application/SeedBootstrapExecutor.java` — matchIds enqueue 제거, seededAt 갱신
- `ingest/application/IngestMatchDetail.java` — expandParticipants 제거
- `ingest/infra/persistence/MatchQueueDispatcherImpl.java` — patch+tier 우선순위 쿼리
- `coverage/infra/persistence/entity/CoverageBucket.java` — 새 컬럼 추가
- `common/infra/persistence/entity/Summoner.java` — seededAt, matchIdsCollectedAt 추가
- `common/infra/persistence/repository/SummonerJpaRepository.java` — Stage 2 조회 쿼리
- `src/main/resources/application.yml` — 새 설정 추가, 기존 WorkItem/refresh 설정 제거

### 신규 생성
- `pipeline/application/PipelineRunner.java`
- `pipeline/application/DailySeedRunner.java`
- `pipeline/application/CollectMatchIdsRunner.java`
- `common/infra/riot/budget/DailyBudgetTracker.java`
- `common/infra/persistence/entity/DailyPipelineState.java`
- `common/infra/persistence/repository/DailyPipelineStateJpaRepository.java`

---

## Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| Phase 1에서 WorkItem 참조 정리 누락으로 컴파일 에러 | 삭제 전 `grep -r "WorkItem\|WorkerPool\|CoverageSchedulingService"` 로 참조 전수 확인 |
| Stage 1 API 소비가 Stage 3 예산 잠식 | `DailyBudgetTracker` 상한 2,000 req 엄격 적용, 초과 즉시 중단 |
| PipelineRunner가 Stage 1/2에서 Stage 3를 오래 block | Stage 1: 1 page = 1 API call 단위, Stage 2: 10~20 summoner 단위로 실행 후 즉시 루프 복귀 |
| 패치 전환 중 DIA/EME 순회 데이터 일관성 | Stage 2에서 현재 패치 matchIds만 enqueue. 이전 패치 항목은 match_queue에서 자연 소진 |
| 자정 경계에서 DailySeedRunner 상태 불일치 | `dailySeedResetAt < TODAY` 조건으로 리셋. DailyPipelineState 이전 날 레코드는 보존 |
| DB 마이그레이션 중 서비스 중단 | 새 컬럼 전부 nullable → `ALTER TABLE ADD COLUMN` 무중단 적용 가능 |

---

## Success Criteria

- [ ] `workitem/` 패키지, `CoverageScheduler`, `CoverageSchedulingService` 완전 제거
- [ ] Refresh 모듈 완전 제거, 10명 증폭 로직 완전 제거
- [ ] GM/M/C summoner가 매일 전체 갱신됨
- [ ] DIA/EME summoner가 division+page 단위로 일일 구간 처리됨
- [ ] Stage 2가 최근 seeded summoner를 우선 처리하고, tier별 matchCount 차등 적용됨
- [ ] Stage 3가 상시 실행되며 patch+tier 기반 우선순위로 match 처리
- [ ] Stage 1/2 예산 소진 시 자동으로 Stage 3 전용 모드 전환
- [ ] 테스트 커버리지 80% 이상

---

## 검증 쿼리

```sql
-- Stage 1 진행 상태
SELECT * FROM daily_pipeline_state WHERE pipeline_date = CURRENT_DATE;

-- Stage 2 대상 summoner 수
SELECT count(*) FROM summoner
WHERE seeded_at IS NOT NULL
  AND (match_ids_collected_at IS NULL OR match_ids_collected_at < seeded_at);

-- match_queue 상태
SELECT status, count(*) FROM match_queue GROUP BY status;

-- patch+tier별 match_queue 분포
SELECT patch, tier, status, count(*) FROM match_queue
GROUP BY patch, tier, status ORDER BY patch DESC, tier;
```
