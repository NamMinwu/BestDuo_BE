# bottom_duo_raw 생성을 위한 파이프라인 구조 설계

> 작성일: 2026-04-09
> 대상: `CoverageSchedulingService` + `WorkerPool` 기반 workItem 파이프라인

---

## 1. 현재 데이터 파이프라인

```
CoverageBucket (patch+tier별 목표)
    ↓
CoverageSchedulingService.determineNextWorkItem()
    ↓
WorkerPool (Virtual Thread 1개)
    ├── SEED_SUMMONERS       → SeedBootstrapExecutor  → summoner 등록 → match_queue(READY) 등록
    ├── REFRESH_SUMMONERS    → RefreshBatchExecutor   → 최근 matchId → match_queue(READY) 갱신
    └── INGEST_MATCH_DETAIL  → MatchIngestWorker      → ★ bottom_duo_raw 저장 ★
```

### 핵심 테이블 관계

```
coverage_bucket (patch, tier, target/current count, status)
    └── work_item (type, status, patch, tier, priority, batch_limit)
            └── match_queue (match_id, status, collection_tier)
                    └── bottom_duo_raw (match_id, team_id, adc_champion_id, sup_champion_id, win, patch, tier)
                            └── bottom_duo_stat_agg (집계 뷰)
```

---

## 2. 현재 구조의 문제점

| # | 문제 | 위치 | 심각도 |
|---|------|------|--------|
| 1 | **전역 카운트 의존** — `countByLastKnownTier(tier)`에 patch 필터 없음. 다른 patch의 summoner가 SEED 결정을 왜곡 | `CoverageSchedulingService.java:68-93` | High |
| 2 | **단일 Virtual Thread 병목** — SEED/REFRESH/INGEST가 1개 스레드에서 순차 실행 | `WorkerPool.java:33` | Medium |
| 3 | **풀 카운트 쿼리** — 매 30초마다 `COUNT(DISTINCT match_id)` 풀 스캔. 데이터 증가 시 비용 폭증 | `CoverageBucketCountJpaRepository.java` | Medium |
| 4 | **이중 큐 락** — workItem 레벨 pickAndLock + match_queue 레벨 pickAndLock 이중 관리 | `MatchIngestWorker.java` | Low |
| 5 | **두 파이프라인 경합** — `ExecutionOrchestrator`와 `CoverageScheduler`가 동일 `match_queue`에 동시 접근 | `ExecutionPipeline.java` | High |

---

## 3. 설계 목표

1. `CoverageBucket`이 patch+tier 단위로 **정확한 workItem 결정**을 내릴 수 있도록 bucket-local 상태 기반 스케줄링
2. 매 사이클마다 풀 스캔 없이 **점진적 카운트 추적**으로 성능 개선
3. **단일 스레드 병목 해소** — Riot API rate limit 내에서 최대 throughput
4. 두 파이프라인의 **경합 제거** — 역할 명확화 또는 통합
5. bucket SUFFICIENT 도달 시 **집계 자동 트리거**

---

## 4. Phase별 구현 계획

### Phase 1: Bucket-Local 스케줄링 (정확도 핵심)

**목적**: `determineNextWorkItem()`이 전역 카운트 대신 해당 `bucket(patch+tier)` 범위 데이터만 기반으로 결정하도록 개선

#### Step 1.1: CoverageBucket 엔티티에 bucket-local 캐시 필드 추가

- **파일**: `coverage/infra/persistence/entity/CoverageBucket.java`
- **변경**: `localSummonerCount`, `localReadyMatchQueue`, `localRecentIngestCount` 캐시 필드 추가
- **이유**: 전역 카운트 쿼리를 제거하고 bucket이 자기 상태만으로 다음 workItem 타입 결정 가능
- **위험도**: Medium — 스키마 변경 + migration 필요

#### Step 1.2: SummonerJpaRepository에 patch+tier 필터 쿼리 추가

- **파일**: `common/infra/persistence/repository/SummonerJpaRepository.java`
- **변경**: `countByLastKnownTier(tier)` → `countByLastKnownTierAndPatch(tier, patch)` 추가
- **이유**: SEED 결정이 해당 bucket의 summoner pool 크기를 정확히 반영
- **위험도**: Low

#### Step 1.3: IngestQueueStatsJpaRepository에 patch 필터 추가

- **파일**: `common/infra/persistence/repository/IngestQueueStatsJpaRepository.java`
- **변경**: `countReadyByTier`, `countDoneInLastMinutesByTier`에 patch 파라미터 추가
- **선결 조건**: match_queue에 patch 컬럼 추가 필요 (기존 row: `DEFAULT 'UNKNOWN'`)
- **위험도**: Medium — match_queue 스키마 변경 수반

#### Step 1.4: CoverageSchedulingService.determineNextWorkItem() 리팩터링

- **파일**: `coverage/application/CoverageSchedulingService.java`
- **변경**: 전역 쿼리를 Step 1.1~1.3의 bucket-local 쿼리로 교체
- **이유**: 각 bucket이 자기 데이터 상태에 맞는 workItem을 생성하도록 변경
- **위험도**: High — 기존 threshold 로직 의미 변경, 테스트 전면 수정 필요

---

### Phase 2: 점진적 카운트 추적 (즉시 성능 개선)

**목적**: 매 스케줄 사이클마다 `COUNT(DISTINCT match_id)` 풀 스캔을 제거하고 증분 업데이트로 전환

#### Step 2.1: BottomDuoRawSaverImpl에서 실제 삽입 건수 반환

- **파일**: `ingest/infra/persistence/BottomDuoRawSaverImpl.java`
- **변경**: `saveAllIdempotent` 반환 타입을 `int`(실제 삽입 건수)로 변경. `DataIntegrityViolationException` catch 시 카운트 제외
- **이유**: 점진적 카운트 정확도 보장
- **위험도**: Low

#### Step 2.2: IngestMatchDetail에서 bucket 카운트 증분 업데이트

- **파일**: `ingest/application/IngestMatchDetail.java`
- **변경**: `saveBottomDuoRaws()` 완료 후 실제 삽입 건수를 기반으로 `bucket.incrementMatchCount(delta)` 호출
- **이유**: O(N) 풀 카운트 → O(1) 증분 업데이트
- **위험도**: Medium — 중복 삽입(idempotent) 시 정확성 보장 필요

#### Step 2.3: CoverageBucket에 점진적 업데이트 메서드 추가

- **파일**: `coverage/infra/persistence/entity/CoverageBucket.java`
- **변경**: `incrementMatchCount(int delta)` 메서드 추가. 기존 `refreshCount(long newCount)`는 하루 1회 보정용으로만 유지
- **이유**: 매 ingest 완료 시 즉시 bucket 상태 반영
- **위험도**: Low

---

### Phase 3: Worker 처리량 최적화

**목적**: 실제 throughput 측정 후 bottleneck을 파악하고, 필요 시 Riot API rate limit 내 최대 throughput 확보

> **선행 조건**: 구현 전 반드시 Step 3.0 측정을 먼저 수행한다.
> Virtual Thread 추가가 유효한 건 단일 스레드가 rate limit을 포화시키지 못할 때뿐이다.
> INGEST 1 workItem = 20 sequential API calls이며, latency에 따라 단일 스레드도 충분할 수 있다.

#### Step 3.0: 실제 throughput 측정 (구현 전 필수)

- **파일**: `ingest/application/MatchIngestWorker.java`
- **변경**: workItem 실행 시 소요 시간과 API calls/s 로깅 추가

```java
long start = System.currentTimeMillis();
var result = ingestMatchDetail.execute(matchId, tier);
long elapsed = System.currentTimeMillis() - start;
// workItem 단위가 아닌 개별 call 기준으로 throughput 측정
log.info("[INGEST] matchId={} elapsed={}ms", matchId, elapsed);
```

- **판단 기준**:
  - avg latency ≤ 50ms → 단일 스레드로 20 req/s 도달 가능 → **Phase 3 불필요**
  - avg latency > 50ms → 단일 스레드 throughput < rate limit → **Step 3.1~3.3 진행**
- **위험도**: Low

#### Step 3.1: WorkItemProperties에 동시성 설정 추가

- **파일**: `config/WorkItemProperties.java`
- **변경**: `workerConcurrency` 설정 추가 (기본값 1, 최대 3~5)
- **선행 조건**: Step 3.0에서 단일 스레드 throughput 부족 확인 후 진행
- **위험도**: Low

#### Step 3.2: WorkerPool을 N개 Virtual Thread로 확장

- **파일**: `workitem/application/WorkerPool.java`
- **변경**: 단일 `workerThread` → `List<Thread>` 기반 N개 워커
- **이유**: 단일 스레드의 API I/O 대기 시간을 다른 요청으로 채워 rate limit 예산 활용률 극대화.
  SEED/REFRESH/INGEST 모두 동일한 rate limit 예산을 공유하므로 스레드 증가가 총 호출 수를 늘리는 것이 아니라 idle 낭비를 줄이는 목적임.
- **최적 스레드 수**: `rate_limit(20) × avg_latency_sec` — Step 3.0 측정값으로 계산
- **주의**: `RiotRateLimitInterceptor.durationUntilAvailable()` 공유 자원 thread-safe 동기화 확인 필요
- **위험도**: Medium

#### Step 3.3: duplicatePendingLimit을 타입별로 분리

- **파일**: `config/WorkItemProperties.java`, `coverage/application/CoverageSchedulingService.java:96-104`
- **변경**: `seedPendingLimit`, `refreshPendingLimit`, `ingestPendingLimit`으로 분리. INGEST는 더 높은 limit 허용
- **이유**: match_queue에 READY가 많을 때 INGEST 여러 workItem 동시 처리 가능
- **위험도**: Low

---

### Phase 4: 파이프라인 역할 명확화

**목적**: `ExecutionOrchestrator`와 `CoverageScheduler`의 경합 제거

#### 선택지

**(A) 통합 (권장)**: `ExecutionOrchestrator` deprecated 처리, 모든 실행을 `CoverageScheduler` + `WorkerPool`로 통합
- `ExecutionOrchestrator`의 budget 개념은 `CoverageBucket.targetMatchCount`로 대체
- 수동 실행이 필요하면 workItem 직접 생성하는 admin API 제공

**(B) 역할 분리**: `ExecutionOrchestrator`는 수동 트리거 / 일회성 대량 수집 전용
- `CoverageScheduler`와 동시 실행되지 않도록 mutex 또는 feature flag 추가

- **파일**: `orchestration/application/ExecutionOrchestrator.java`, `ExecutionPipeline.java`
- **위험도**: High — ExecutionOrchestrator 제거 시 수동 실행 경로 대안 필요

---

### Phase 5: 집계 자동화

**목적**: `bottom_duo_raw` 데이터가 충분히 쌓이면 자동으로 집계 트리거

#### Step 5.1: AGGREGATE_STATS WorkItemType 추가

- **파일**: `workitem/domain/model/WorkItemType.java`
- **변경**: `AGGREGATE_STATS` 타입 추가
- **위험도**: Low

#### Step 5.2: CoverageSchedulingService에 SUFFICIENT 전이 트리거 추가

- **파일**: `coverage/application/CoverageSchedulingService.java`
- **변경**: `bucket.incrementMatchCount()` 후 status가 COLLECTING → SUFFICIENT 전이 시 `AGGREGATE_STATS` workItem 발행
- **선결 조건**: Phase 2 완료 (카운트 정확도 확보 후 SUFFICIENT 전이 시점이 신뢰 가능)
- **위험도**: Low

#### Step 5.3: AggregateStatsWorker 구현

- **신규 파일**: `workitem/application/worker/AggregateStatsWorker.java`
- **변경**: `WorkerContract` 구현, 기존 `AggregateBottomDuoStats.execute()` 호출
- **위험도**: Low

---

## 5. 추천 구현 순서

```
Phase 2 (즉시 성능 개선)
    → Phase 1 (정확도 핵심, patch-local 스케줄링)
        → Phase 3 (throughput 병목 해소)
            → Phase 5 (집계 자동화)
                → Phase 4 (파이프라인 통합, 가장 큰 구조 변경)
```

각 Phase는 독립적으로 배포 가능하며, 이전 Phase 완료 전에도 기존 기능이 깨지지 않는다.

---

## 6. 위험 요소 및 완화 전략

| 위험 | 완화 |
|------|------|
| match_queue에 patch 컬럼 추가 시 기존 데이터 마이그레이션 | 기존 row `DEFAULT 'UNKNOWN'` 설정, 신규만 정확한 patch 기록 |
| 점진적 카운트와 실제 DB 카운트 드리프트 | 하루 1회 `refreshCount(fullCount)` 보정 스케줄 유지 |
| Worker 동시성 증가 시 Riot API 429 증가 | 기존 `DualWindowRateLimiter` + `RiotRateLimitInterceptor`가 방어 역할 유지 |
| ExecutionOrchestrator 제거 시 수동 실행 불가 | admin API에서 workItem 직접 생성 엔드포인트 제공 |

---

## 7. 성공 기준

- [ ] `CoverageSchedulingService`가 bucket(patch+tier) 범위 데이터만으로 workItem 타입을 결정한다
- [ ] `bottom_duo_raw` 카운트가 매 ingest 완료 시 즉시 bucket에 반영된다
- [ ] 풀 카운트 쿼리(`COUNT(DISTINCT match_id)`)가 스케줄 사이클에서 제거되고 보정용으로만 남는다
- [ ] `WorkerPool`이 설정 가능한 동시성으로 운영된다
- [ ] `CoverageBucket`이 SUFFICIENT 도달 시 자동으로 집계가 트리거된다
- [ ] 두 파이프라인(Orchestrator vs CoverageScheduler) 간 경합이 제거된다

---

## 8. 주요 참조 파일

| 파일 | 역할 |
|------|------|
| `coverage/application/CoverageSchedulingService.java` | 핵심 스케줄링 결정 로직 (개선 대상) |
| `coverage/application/CoverageScheduler.java` | 30초 주기 트리거 |
| `coverage/infra/persistence/entity/CoverageBucket.java` | bucket 엔티티 (patch+tier+목표 카운트) |
| `workitem/application/WorkerPool.java` | 단일 Virtual Thread 워커 (확장 대상) |
| `ingest/application/IngestMatchDetail.java` | bottom_duo_raw 생성 핵심 로직 |
| `ingest/infra/persistence/BottomDuoRawSaverImpl.java` | 멱등 저장 (반환값 개선 대상) |
| `orchestration/application/ExecutionPipeline.java` | 병렬 파이프라인 (통합/분리 대상) |
| `config/WorkItemProperties.java` | threshold/batch 설정 |
| `coverage/infra/persistence/repository/CoverageBucketCountJpaRepository.java` | 풀 카운트 쿼리 (제거 대상) |
