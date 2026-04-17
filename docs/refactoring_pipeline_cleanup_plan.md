# 리팩토링 계획: 구버전 파이프라인 제거 및 `pipeline` 패키지 기반 리팩토링

## Overview

현재 코드베이스에는 두 개의 데이터 수집 파이프라인이 공존합니다.

- **신버전 (유지)**: `pipeline` 패키지의 `PipelineRunner` (가상 스레드 단일 루프, Stage 1→2→3)
- **구버전 (삭제)**: `orchestration` 패키지의 `ExecutionRequestPoller + ExecutionRequestWorker + ExecutionOrchestrator + ExecutionPipeline` (DB 기반 요청 큐 + seed/ingest 2-phase)

이 계획은 구버전을 제거하고 `pipeline` 패키지를 유일한 수집 경로로 고정한 뒤 전반적으로 리팩토링합니다.

---

## 목표

- 데이터 수집 경로는 `pipeline.application.PipelineRunner`(+ `DailySeedRunner`, `CollectMatchIdsRunner`, Stage 3의 `MatchIngestWorker`)로만 고정
- 구버전 `orchestration` 파이프라인 코드/설정/DB 테이블 참조 모두 정리
- 불필요한 Admin API(`CoverageBucket` 생성 엔드포인트) 제거
- 테스트 GREEN 유지 (80%+ 커버리지 목표)
- 단계별로 독립 머지 가능한 phase로 구성

---

## 현황 파악

### 신버전 (유지 대상) — `pipeline` 패키지

| 파일 | 역할 |
|------|------|
| `pipeline/application/PipelineRunner.java` | 단일 가상 스레드 루프, Stage 1→2→3 우선순위 |
| `pipeline/application/DailySeedRunner.java` | Stage 1 |
| `pipeline/application/CollectMatchIdsRunner.java` | Stage 2 |
| `config/PipelineProperties.java` | `pipeline.*` 설정 |

### 신버전이 공유하는 것 (유지)

- `ingest/application/MatchIngestWorker.java` — Stage 3 진입점. `executeWithPriority(limit, tier, patch)`는 `PipelineRunner`가 호출하는 핵심 API
- `ingest/application/IngestMatchDetail.java` — 매치 상세 저장
- `ingest/application/IngestQueueStats.java`, `AdminQueueController`, `MatchQueueAdminController` — 운영/디버그 엔드포인트
- `ingest/presentation/api/IngestController.java` — 단건 강제 수집
- `seed/application/SeedBootstrapExecutor` — `DailySeedRunner`가 사용
- `coverage/infra/persistence/entity/CoverageBucket.java` — Stage 1 seed 진행 상태 저장소 (유지)
- `coverage/presentation/api/AdminCoverageController.java` — GET 엔드포인트만 유지 (POST 제거)
- `common/**`, `coverage/**`, `aggregate/**` — 도메인·인프라 공유

### 구버전 (삭제 대상) — `orchestration` 패키지 + 관련 설정

**Java 파일**
- `orchestration/application/ExecutionRequestPoller.java`
- `orchestration/application/ExecutionRequestWorker.java`
- `orchestration/application/ExecutionOrchestrator.java`
- `orchestration/application/ExecutionPipeline.java`
- `orchestration/application/ExecutionRequestService.java`
- `orchestration/presentation/api/AdminRunController.java`
- `orchestration/presentation/api/dto/ExecutionCreateRequest.java`
- `orchestration/presentation/api/dto/ExecutionRequestResponse.java`
- `orchestration/application/port/ExecutionRequestFinder.java`
- `orchestration/application/port/ExecutionRequestSaver.java`
- `orchestration/application/port/ExecutionRequestStatusUpdater.java`
- `orchestration/infra/persistence/ExecutionRequestFinderImpl.java`
- `orchestration/infra/persistence/ExecutionRequestSaverImpl.java`
- `orchestration/infra/persistence/ExecutionRequestStatusUpdaterImpl.java`
- `orchestration/infra/persistence/entity/ExecutionRequest.java` — DB 테이블 `session_execution_request`
- `orchestration/infra/persistence/entity/ExecutionLog.java` — DB 테이블 `session_execution_log`
- `orchestration/infra/persistence/repository/ExecutionRequestJpaRepository.java`
- `orchestration/infra/persistence/repository/ExecutionLogJpaRepository.java`

**설정**
- `config/DailyRunProperties.java` — `daily-run.*` 전체가 구버전 전용
- `application.yml` — `daily-run:` 블록, `execution-request.worker.*` 블록

**테스트**
- `src/test/java/com/bestduo_BE/orchestration/application/ExecutionPipelineTest.java`

### `CoverageBucketService` — 부분 삭제

`CoverageBucket` 엔티티는 `DailySeedRunner`의 핵심 상태 저장소이므로 유지합니다.
단, 버킷 생성은 패치 업데이트 시 SQL 마이그레이션 스크립트로 관리하면 충분하므로 create API는 제거합니다.

| 항목 | 처리 |
|------|------|
| `CoverageBucketService.create()` | 삭제 |
| `AdminCoverageController` POST `/admin/coverage` | 삭제 |
| `CoverageBucketCreateRequest` DTO | 삭제 |
| `CoverageBucketAlreadyExistsException` | 삭제 |
| `CoverageBucketService.get()` / `getAll()` | 유지 (모니터링용) |
| `AdminCoverageController` GET 엔드포인트 | 유지 |
| `CoverageBucketNotFoundException` | 유지 |

---

## 구현 단계

### Phase 1: 구버전 진입점 무력화 (비파괴적)

**Step 1. `execution-request.worker.enabled` 기본값 false 강제**
- File: `src/main/resources/application.yml`
- Action: `execution-request.worker.enabled`를 명시적 false로 고정 + 주석으로 제거 예정 표시
- Why: 운영 중 구버전 폴러가 `PipelineRunner`와 동시 구동되어 Riot 키를 이중 소진하는 상황 차단
- Risk: Low

**Step 2. `AdminRunController` deprecate 표기**
- File: `orchestration/presentation/api/AdminRunController.java`
- Action: `@Deprecated` 부여. 외부 호출 여부 확인 후 Phase 2에서 삭제
- Risk: Low

---

### Phase 2: 구버전 코드 삭제 (주 삭제 단계)

**Step 3. `orchestration` 패키지 전체 제거**
- Files: 위 "삭제 대상" 섹션의 `orchestration/**` 전체
- Action: refactor-cleaner 에이전트로 dead-code 검증 후 디렉터리 일괄 삭제
- Dependencies: Step 1, 2
- Risk: Medium — `MatchIngestWorker`의 미사용 오버로드 함께 정리 필요

**Step 4. `DailyRunProperties` 및 YAML `daily-run:` 블록 제거**
- Files: `config/DailyRunProperties.java`, `application.yml`
- Action: 파일 삭제, YAML에서 `daily-run:` 및 `execution-request:` 블록 제거
- Dependencies: Step 3
- Risk: Low

**Step 5. `ExecutionPipelineTest` 제거**
- File: `src/test/java/com/bestduo_BE/orchestration/application/ExecutionPipelineTest.java`
- Action: 파일 삭제. `@MockBean` 등으로 구버전 빈을 참조하는 다른 테스트 전수 검사
- Dependencies: Step 3
- Risk: Low

**Step 6. DB 테이블 마이그레이션 노트 추가**
- Action: `session_execution_request`, `session_execution_log` 테이블을 운영 DB에서 drop하는 수동 마이그레이션 스크립트 문서화
- Why: JPA `ddl-auto=update`는 drop을 수행하지 않음
- Risk: Medium — 운영 반영 시 drop 시점 조정 필요

---

### Phase 3: `CoverageBucket` create API 제거

**Step 7. `CoverageBucketService.create()` 및 관련 코드 삭제**
- Files:
  - `coverage/application/CoverageBucketService.java` — `create()` 메서드 제거
  - `coverage/presentation/api/AdminCoverageController.java` — POST 엔드포인트 제거
  - `coverage/presentation/api/dto/CoverageBucketCreateRequest.java` — 삭제
  - `coverage/application/exception/CoverageBucketAlreadyExistsException.java` — 삭제
- Why: 버킷 생성은 패치 업데이트 시 SQL 마이그레이션으로 관리하면 충분. API 불필요
- Risk: Low — GET 엔드포인트와 `CoverageBucket` 엔티티는 영향 없음

---

### Phase 4: `MatchIngestWorker` 단일 진입점화 및 예산 레이어 정리

**Step 8. `MatchIngestWorker` API 축소**
- File: `ingest/application/MatchIngestWorker.java`
- Action: `executeWithPriority(int, Tier, String)`를 주 API로 남기고, 미사용 오버로드 제거. 관리 API가 쓰는 경로만 보존
- Dependencies: Step 3
- Risk: Medium — `MatchIngestWorkerTest` 업데이트 필요

**Step 9. `BudgetExhaustedException` / `RiotRequestBudget` 사용처 정리**
- Files: `common/infra/riot/budget/*.java`
- Action: `RiotRequestBudget`(ThreadLocal) 제거 여부 판단. 참조 전수 검색 후 결정
- Dependencies: Step 3, 8
- Risk: Medium — Riot 클라이언트 인터셉터 참조 가능성

---

### Phase 5: `pipeline` 패키지 내 리팩토링 (품질 개선)

**Step 10. `DailySeedRunner` JSON 문자열 검사 → 도메인 메서드로 캡슐화**
- File: `pipeline/application/DailySeedRunner.java`
- Action: `state.getSeedCompletedTiers().contains("\"" + tier.name() + "\"")` 패턴 제거. `DailyPipelineState`에 `isSeedTierCompleted(Tier)` / `markSeedTierCompleted(Tier)` 도메인 메서드 도입 (기존 JSON 배열 그대로, backward-compatible)
- Why: DB 직렬화 디테일이 응용 서비스로 새어나옴 (캡슐화 위반)
- Risk: Medium

**Step 11. `PipelineRunner` 복원력 보강**
- File: `pipeline/application/PipelineRunner.java`
- Action: `sleep(5_000L)` 매직 넘버 → `PipelineProperties.errorBackoffMs`로 치환. `@PreDestroy`로 가상 스레드 종료 훅 추가
- Risk: Low

**Step 12. `CollectMatchIdsRunner` 예외 처리 정책 정리**
- File: `pipeline/application/CollectMatchIdsRunner.java`
- Action: 실패한 summoner의 예산 차감 포함 여부 및 재시도 정책 정리 후 반영
- Risk: Medium — 운영 지표에 영향

**Step 13. 공통 상수·매직 넘버 추출**
- Files: `pipeline/application/*.java`
- Action: `PRIORITY_COLLECT = 50`, `RATE_LIMIT_SLEEP_MS`, `QUEUE = "RANKED_SOLO_5x5"` 등을 `PipelineProperties` 또는 `PipelineConstants`로 일원화
- Risk: Low

**Step 14. 운영/관리 API 경로 정리**
- Files: `ingest/presentation/api/AdminQueueController.java`, `MatchQueueAdminController.java`, `IngestController.java`
- Action: 매핑 충돌 검토 후 `/admin/...` 구조로 일원화
- Dependencies: Step 3 완료 후
- Risk: Low

---

### Phase 6: refactor-cleaner 전체 sweep

**Step 15. refactor-cleaner 에이전트로 dead-code 전수 검사**
- Action: 삭제 후 남은 미사용 port 인터페이스, DTO, import 전수 검사
- Dependencies: Phase 2, 3, 4 완료
- Risk: Low

**Step 16. `@DisplayName` 한글 규칙 일관성 검사**
- Action: 신규/수정 테스트 `@DisplayName` 한글화 확인
- Risk: Low

---

## 테스트 전략

- **단위 테스트**: `PipelineRunnerTest`, `DailySeedRunnerTest`, `CollectMatchIdsRunnerTest`, `MatchIngestWorkerTest` — 삭제 후 컴파일·GREEN 유지
- **삭제 검증**: `orchestration/**` 제거 후 `./gradlew compileJava test`로 참조 누락 확인
- **통합 테스트**: Stage 전환 (1→2→3, budget 소진, 429 backoff) 커버리지 확인
- **DB 검증**: 로컬 dev DB에서 `session_execution_request` / `session_execution_log` drop 후 Boot 기동 성공 확인

---

## 위험 요소 및 대응

| 위험 | 심각도 | 대응 |
|------|--------|------|
| 운영 환경에서 구버전 폴러 동시 실행 → Riot 키 이중 소진 | High | Phase 1에서 YAML 기본값 false 강제 → Phase 2에서 코드 자체 제거 |
| `session_execution_log`에 분석용 이력 존재 | Medium | Step 6 마이그레이션 노트에 백업 절차 포함 |
| `MatchIngestWorker` 관리 API 경로의 내부 사용 가능성 | Medium | Step 8에서 호출부 전수 검색 후 결정 |
| `RiotRequestBudget`(ThreadLocal)이 Riot 클라이언트 인터셉터에서 참조 시 NPE | Medium | Step 9 전 전체 grep으로 참조 확인 |
| `DailyPipelineState.seedCompletedTiers` JSON 직렬화 변경으로 기존 DB row 파손 | Medium | Step 10을 backward-compatible하게 설계 |

---

## 완료 기준

- [ ] `orchestration/**` 패키지가 저장소에서 완전히 삭제됨
- [ ] `config/DailyRunProperties.java` 삭제됨
- [ ] `application.yml`에 `daily-run:`, `execution-request:` 섹션 없음
- [ ] `PipelineRunner`가 유일한 상시 수집 루프
- [ ] `CoverageBucket` create API 및 관련 코드 삭제됨
- [ ] 기존 테스트 전체 GREEN + 커버리지 80% 유지
- [ ] `grep -R "ExecutionPipeline\|ExecutionOrchestrator\|DailyRunProperties"` 결과 0건
- [ ] 운영 DB의 `session_execution_request`, `session_execution_log` drop 절차가 문서화됨
- [ ] `MatchIngestWorker` 공용 API가 `executeWithPriority(...)` 중심으로 정리됨

---

## 파일 경로 요약

### 삭제 대상

```
src/main/java/com/bestduo_BE/orchestration/                         ← 디렉터리 전체
src/main/java/com/bestduo_BE/config/DailyRunProperties.java
src/test/java/com/bestduo_BE/orchestration/                         ← 디렉터리 전체
src/main/java/com/bestduo_BE/coverage/presentation/api/dto/CoverageBucketCreateRequest.java
src/main/java/com/bestduo_BE/coverage/application/exception/CoverageBucketAlreadyExistsException.java
```

### 수정 대상

```
src/main/resources/application.yml                                   ← daily-run, execution-request 블록 제거
src/main/java/com/bestduo_BE/coverage/application/CoverageBucketService.java     ← create() 제거
src/main/java/com/bestduo_BE/coverage/presentation/api/AdminCoverageController.java  ← POST 제거
src/main/java/com/bestduo_BE/ingest/application/MatchIngestWorker.java
src/main/java/com/bestduo_BE/pipeline/application/PipelineRunner.java
src/main/java/com/bestduo_BE/pipeline/application/DailySeedRunner.java
src/main/java/com/bestduo_BE/pipeline/application/CollectMatchIdsRunner.java
src/main/java/com/bestduo_BE/config/PipelineProperties.java
```

### 유지 대상 (핵심)

```
src/main/java/com/bestduo_BE/pipeline/application/*.java
src/main/java/com/bestduo_BE/ingest/**
src/main/java/com/bestduo_BE/seed/application/SeedBootstrapExecutor.java
src/main/java/com/bestduo_BE/coverage/infra/persistence/entity/CoverageBucket.java
src/main/java/com/bestduo_BE/coverage/application/CoverageBucketService.java    ← get/getAll만
src/main/java/com/bestduo_BE/coverage/presentation/api/AdminCoverageController.java  ← GET만
src/main/java/com/bestduo_BE/common/**
src/main/java/com/bestduo_BE/aggregate/**
```
